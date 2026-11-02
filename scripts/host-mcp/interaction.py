"""Small ADB accessibility driver; no Android instrumentation or shell endpoint.

Interaction(device, check): device(*adb_args) returns decoded stdout, and check()
raises when cancellation/deadline/ownership fails. The caller serializes access.
validate_steps() validates the entire batch before any device mutation. perform()
also validates individual steps. Selectors are exact target dictionaries using id,
text, description, or ref. Ref/coordinate actions require a current snapshot_id;
all input actions invalidate it. Text appends printable ASCII, without clearing.
"""
import re
import shlex
import time
import uuid
import xml.etree.ElementTree as ET

ACTIONS = {'tap', 'long_press', 'swipe', 'type', 'key', 'wait_for', 'assert', 'screenshot'}
KEYS = {'back':4, 'home':3, 'enter':66, 'return':66, 'delete':67, 'tab':61, 'escape':111, 'recents':187}
FIELDS = {
    'tap': {'target', 'snapshot_id'},
    'long_press': {'target', 'snapshot_id', 'duration_ms'},
    'swipe': {'direction', 'from', 'to', 'snapshot_id', 'duration_ms'},
    'type': {'text', 'target', 'snapshot_id'},
    'key': {'key', 'times'},
    'wait_for': {'target', 'exists', 'timeout'},
    'assert': {'target', 'exists'},
    'screenshot': {'label'},
}


def _integer(value, minimum, maximum):
    return type(value) is int and minimum <= value <= maximum


def _target_schema(target, allow_coordinates=True):
    if not isinstance(target, dict) or not target:
        raise ValueError('target must be a nonempty selector object')
    if set(target) == {'x','y'} and allow_coordinates:
        if not all(_integer(v, 0, 100000) for v in target.values()):
            raise ValueError('Invalid coordinate target')
        return
    if not set(target) <= {'id','text','description','ref'}:
        raise ValueError('Unknown target selector')
    if 'ref' in target and len(target) != 1:
        raise ValueError('ref cannot be combined with other selectors')
    if not all(isinstance(v,str) and 0 < len(v) <= 2048 for v in target.values()):
        raise ValueError('Selector values must be nonempty strings')


def validate_steps(steps):
    """Reject malformed/unsupported batches before an earlier step can mutate UI."""
    if not isinstance(steps,list) or not 1 <= len(steps) <= 20:
        raise ValueError('A batch must contain 1 to 20 steps')
    for step in steps:
        if not isinstance(step,dict) or not isinstance(step.get('action'),str) or step['action'] not in ACTIONS:
            raise ValueError('Unknown action')
        action = step['action']
        if set(step) - {'action'} - FIELDS[action]:
            raise ValueError('Unknown action fields')
        if 'snapshot_id' in step and (not isinstance(step['snapshot_id'],str) or not re.fullmatch('[0-9a-f]{32}',step['snapshot_id'])):
            raise ValueError('Invalid snapshot_id')
        if action in {'tap','long_press','wait_for','assert'} and 'target' not in step:
            raise ValueError('Action requires target')
        if 'target' in step:
            _target_schema(step['target'], action not in {'wait_for','assert'})
            if action in {'wait_for','assert'} and 'ref' in step['target']:
                raise ValueError('Waits/assertions require stable selectors')
        if 'target' in step and ('ref' in step['target'] or set(step['target'])=={'x','y'}) and 'snapshot_id' not in step:
            raise ValueError('Ref/coordinate actions require snapshot_id')
        if action == 'type':
            text = step.get('text')
            if not isinstance(text,str) or not 1 <= len(text) <= 4096:
                raise ValueError('text must contain 1 to 4096 characters')
            if any(not 32 <= ord(c) <= 126 for c in text):
                raise ValueError('Only printable ASCII text is supported; no newline or Unicode')
        if action == 'key':
            if not isinstance(step.get('key'),str) or step['key'] not in KEYS or not _integer(step.get('times',1),1,10):
                raise ValueError('Invalid key or repeat count')
        if action == 'swipe':
            directional = 'direction' in step
            positional = 'from' in step or 'to' in step
            if directional == positional:
                raise ValueError('swipe requires direction or both from/to coordinates')
            if directional and (not isinstance(step['direction'],str) or step['direction'] not in {'up','down','left','right'}):
                raise ValueError('Invalid swipe direction')
            if positional:
                if 'snapshot_id' not in step:
                    raise ValueError('Coordinate swipe requires snapshot_id')
                for name in ['from','to']:
                    _target_schema(step.get(name))
                    if set(step[name]) != {'x','y'}:
                        raise ValueError('swipe endpoints require coordinates')
        if 'duration_ms' in step and not _integer(step['duration_ms'],1,2000):
            raise ValueError('duration_ms must be between 1 and 2000')
        if 'exists' in step and type(step['exists']) is not bool:
            raise ValueError('exists must be boolean')
        if 'timeout' in step and (type(step['timeout']) not in {int,float} or not 0 < step['timeout'] <= 10):
            raise ValueError('timeout must be greater than zero and at most 10 seconds')
        if 'label' in step and (not isinstance(step['label'],str) or len(step['label']) > 120):
            raise ValueError('label must be a string of at most 120 characters')
    return steps


def _bounds(text):
    match = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',text or '')
    return list(map(int,match.groups())) if match else None


def _intersection(a,b):
    return [max(a[0],b[0]), max(a[1],b[1]), min(a[2],b[2]), min(a[3],b[3])]


def _overlaps(a,b):
    intersection = _intersection(a,b)
    return intersection[2] > intersection[0] and intersection[3] > intersection[1]


def _text_chunks(text):
    # Android input text substitutes literal %s with a space. Split that pair
    # before encoding actual spaces as %s; each call types into the same field.
    chunks = []
    current = ''
    for character in text:
        if len(current) >= 300 or (character == 's' and current.endswith('%')):
            chunks.append(current)
            current = ''
        current += character
    if current:
        chunks.append(current)
    return [shlex.quote(c.replace(' ','%s')) for c in chunks]


class Interaction:
    def __init__(self, device, check=lambda:None):
        self.device = device
        self.check = check
        self.snapshot = None

    def _call(self,*args):
        self.check()
        result = self.device(*args)
        self.check()
        return result

    def invalidate(self):
        self.snapshot = None

    def inspect(self):
        self.invalidate()
        raw = self._call('exec-out','uiautomator','dump','/dev/tty')
        if not isinstance(raw,str) or len(raw) > 2*1024*1024 or '<!DOCTYPE' in raw or '<!ENTITY' in raw:
            raise ValueError('Invalid accessibility XML')
        start = raw.find('<hierarchy')
        end = raw.rfind('</hierarchy>')
        if start < 0 or end < 0:
            raise ValueError('No accessibility XML; inspect screenshot or wait for UI to settle')
        try:
            root = ET.fromstring(raw[start:end+len('</hierarchy>')])
        except ET.ParseError as error:
            raise ValueError('Invalid accessibility XML') from error
        sizes = re.findall(r'(?:Physical|Override) size:\s*(\d+)x(\d+)',self._call('shell','wm','size'))
        if not sizes:
            raise ValueError('Unable to determine screen size')
        width,height = map(int,sizes[-1])
        if root.get('rotation') in {'1','3'}:
            width,height = height,width
        viewport = [0,0,width,height]
        keyboard_bounds = None
        for node in root.iter('node'):
            if 'inputmethod' in node.get('package','').lower():
                bound = _bounds(node.get('bounds'))
                if bound and bound[2]>bound[0] and bound[3]>bound[1]:
                    if keyboard_bounds is None:
                        keyboard_bounds = bound
                    else:
                        keyboard_bounds = [min(keyboard_bounds[0],bound[0]),min(keyboard_bounds[1],bound[1]),max(keyboard_bounds[2],bound[2]),max(keyboard_bounds[3],bound[3])]
        elements = []
        def walk(node,clip):
            bound = _bounds(node.get('bounds'))
            effective = _intersection(clip,bound) if bound else clip
            label = node.get('text','')
            resource = node.get('resource-id','')
            description = node.get('content-desc','')
            interactive = any(node.get(name)=='true' for name in ['clickable','long-clickable','scrollable','focusable','checkable'])
            if bound and (resource or label or description or interactive) and 'inputmethod' not in node.get('package','').lower():
                elements.append(dict(ref='e'+str(len(elements)+1),id=resource.split(':id/')[-1],text=label,description=description,
                    bounds=bound,visible_bounds=effective,clipped=bound!=effective,enabled=node.get('enabled','true')=='true',
                    clickable=node.get('clickable')=='true',scrollable=node.get('scrollable')=='true',focused=node.get('focused')=='true',
                    selected=node.get('selected')=='true',checked=node.get('checked')=='true',type=node.get('class','').split('.')[-1]))
            # Accessibility nodes are virtual semantics, not guaranteed visual
            # clipping layers. Compose sheets can draw sticky footer children
            # beyond a non-scrollable ancestor's reported bounds. Only known
            # scroll viewports constrain descendants; each node itself is still
            # checked against its inherited viewport and the screen.
            child_clip = effective if node.get('scrollable') == 'true' else clip
            for child in node:
                walk(child,child_clip)
        walk(root,viewport)
        if len(elements)>1500:
            raise ValueError('Accessibility tree exceeds 1500 elements')
        self.snapshot = dict(snapshot_id=uuid.uuid4().hex,screen=dict(width=width,height=height),keyboard_bounds=keyboard_bounds,elements=elements)
        return self.snapshot

    def _current(self,step):
        if self.snapshot is None or step.get('snapshot_id') != self.snapshot['snapshot_id']:
            raise ValueError('Expired or missing snapshot_id; inspect before using refs/coordinates')
        return self.snapshot

    def _matches(self,snapshot,target):
        matches = [element for element in snapshot['elements'] if all(element.get(key)==value for key,value in target.items())]
        if len(matches)>1:
            raise ValueError('Target selector is ambiguous; use a unique resource id or combined selectors')
        return matches

    def _point(self,snapshot,target):
        if set(target)=={'x','y'}:
            point = (target['x'],target['y'])
            if not (0 <= point[0] < snapshot['screen']['width'] and 0 <= point[1] < snapshot['screen']['height']):
                raise ValueError('Coordinate lies outside screen')
            return point
        matches = self._matches(snapshot,target)
        if not matches:
            raise ValueError('Target not found; inspect current UI')
        element = matches[0]
        bound = element['bounds']
        if not element['enabled']:
            raise ValueError('Target is disabled')
        if element['clipped'] or bound[0]<0 or bound[1]<0 or bound[2]>snapshot['screen']['width'] or bound[3]>snapshot['screen']['height'] or bound[2]<=bound[0] or bound[3]<=bound[1]:
            raise ValueError('Target is clipped or outside screen; scroll it fully into view')
        keyboard = snapshot['keyboard_bounds']
        if keyboard and _overlaps(bound,keyboard):
            raise ValueError('Target is obscured by keyboard; dismiss or scroll first')
        return ((bound[0]+bound[2])//2,(bound[1]+bound[3])//2)

    def _resolve(self,step,target):
        if 'ref' in target or set(target)=={'x','y'}:
            snapshot = self._current(step)
        else:
            snapshot = self.inspect()
        return self._point(snapshot,target)

    def perform(self,step):
        validate_steps([step])
        self.check()
        action = step['action']
        result = dict(action=action,verified=False)
        if action in {'assert','wait_for'}:
            deadline = time.monotonic()+step.get('timeout',10)
            while True:
                self.check()
                snap = self.inspect()
                matches = self._matches(snap,step['target'])
                visible = bool(matches and not matches[0]['clipped'] and matches[0]['bounds'][2]>matches[0]['bounds'][0] and matches[0]['bounds'][3]>matches[0]['bounds'][1])
                if visible == step.get('exists',True):
                    return dict(action=action,verified=True,snapshot_id=snap['snapshot_id'])
                if action=='assert':
                    raise ValueError('UI assertion failed: target visibility differs from exists')
                if time.monotonic()>=deadline:
                    raise TimeoutError('Timed out waiting for target visibility')
                time.sleep(.25)
        if action=='screenshot':
            return dict(action=action,capture=True,label=step.get('label',''),verified=False)
        if action in {'tap','long_press'} or (action=='type' and 'target' in step):
            x,y = self._resolve(step,step['target'])
            self.invalidate()
            if action=='long_press':
                self._call('shell','input','swipe',str(x),str(y),str(x),str(y),str(step.get('duration_ms',600)))
            else:
                self._call('shell','input','tap',str(x),str(y))
            if action!='type':
                return result
        if action=='type':
            self.invalidate()
            for chunk in _text_chunks(step['text']):
                self._call('shell','input','text',chunk)
        elif action=='key':
            self.invalidate()
            self._call('shell','input','keyevent',*([str(KEYS[step['key']])]*step.get('times',1)))
        elif action=='swipe':
            if 'direction' in step:
                snap = self.inspect()
                w,h = snap['screen']['width'],snap['screen']['height']
                endpoints = {'up':(w//2,h*3//4,w//2,h//4),'down':(w//2,h//4,w//2,h*3//4),'left':(w*3//4,h//2,w//4,h//2),'right':(w//4,h//2,w*3//4,h//2)}
                coordinates = endpoints[step['direction']]
            else:
                snap = self._current(step)
                coordinates = self._point(snap,step['from'])+self._point(snap,step['to'])
            self.invalidate()
            self._call('shell','input','swipe',*(str(n) for n in coordinates),str(step.get('duration_ms',300)))
        return result
