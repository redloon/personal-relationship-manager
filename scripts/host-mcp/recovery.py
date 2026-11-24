"""Normal-app process-death journeys, using only public accessibility controls.

No run-as, debug backdoor, instrumentation APK, database replacement or draft
acceptance requirement. The disposable English-language AVD is cleared once at
installation; the same file-backed database survives every measured restart.
"""
import re
import time
from interaction import Interaction

PACKAGE = 'com.redloon.prm'
CASES = ('saved_records', 'edited_records', 'deleted_records')


def unique_record(elements, prefix, expected=None):
    ids = {e['id'] for e in elements if e['id'].startswith(prefix)}
    if len(ids) != 1 or expected is not None and ids != {expected}:
        raise AssertionError(f'Missing or duplicated {prefix}: expected {expected or "one record"}, got {sorted(ids)}')
    return next(iter(ids))


class RecoveryJourney:
    def __init__(self, device, check=lambda: None, note=lambda text: None):
        self.device = device
        self.ui = Interaction(device, check)
        self.note = note
        self.person = 'Mira Recovery Trial'
        self.tag = 'recoverytrial'
        self.text = dict(EVENT='Lantern recovery note', FACT='Cedar recovery fact', FOLLOW_UP='Orbit recovery intention')
        self.ids = {}
        self.person_id = None
        self.tag_id = None
        self.completed = False

    def step(self, action, **args):
        return self.ui.perform(dict(action=action, **args))

    def wait(self, id, exists=True):
        return self.step('wait_for', target=dict(id=id), exists=exists, timeout=10)

    def tap(self, id):
        self.tap_target(dict(id=id))

    def tap_target(self, target):
        # A wait or inspection already resolved this exact UI. Use its current
        # ref rather than running two more slow uiautomator dumps per tap.
        # Interaction still checks ambiguity, bounds, keyboard and snapshot
        # identity, and invalidates the ref after every input.
        snapshot = self.ui.snapshot
        matches = self.ui._matches(snapshot, target) if snapshot else []
        if not matches or matches[0]['clipped']:
            self.step('wait_for', target=target, timeout=10)
            snapshot = self.ui.snapshot
            matches = self.ui._matches(snapshot, target)
        self.step('tap', target=dict(ref=matches[0]['ref']), snapshot_id=snapshot['snapshot_id'])

    def text_tap(self, text):
        self.tap_target(dict(text=text))

    def write(self, id, text, append=False):
        self.tap(id)
        if append:
            self.device('shell', 'input', 'keyevent', '123')  # MOVE_END
        self.step('type', text=text)
        self.step('key', key='back')  # dismiss the IME before accessing footer controls

    def home(self):
        for _ in range(8):
            snapshot = self.ui.inspect()
            ids = {e['id'] for e in snapshot['elements']}
            if 'dashboard_search' in ids and not ids.intersection({'fact_reader', 'followup_reader', 'add_event_sheet', 'followups_list', 'followup_editor'}):
                return
            if 'nav_tab_home' in ids and not ids.intersection({'fact_reader', 'followup_reader', 'add_event_sheet', 'followups_list', 'followup_editor'}):
                self.tap('nav_tab_home')
            else:
                self.step('key', key='back')
        raise AssertionError('Could not return to Home')

    def search(self, kind, expect=True):
        self.home()
        if any(e['id'] == 'clear_dashboard_search_button' for e in self.ui.inspect()['elements']):
            self.tap('clear_dashboard_search_button')
        self.write('dashboard_search', self.text[kind])
        # Wait for loaded results rather than mistaking the previous query for
        # a durability result. Positive matches are bounded by the job deadline.
        prefix = 'recall_'+{'EVENT':'event','FACT':'fact','FOLLOW_UP':'followup'}[kind]+'_'
        until = time.monotonic()+10
        while True:
            snapshot = self.ui.inspect()
            rows = [e for e in snapshot['elements'] if e['id'].startswith(prefix)]
            if expect and rows:
                record = unique_record(rows, prefix, self.ids.get(kind))
                self.ids[kind] = record
                return record
            if not expect and any(e['id']=='dashboard_empty' for e in snapshot['elements']):
                return None
            if time.monotonic() >= until:
                raise AssertionError(f'Unexpected recall state for {kind}; expected present={expect}')
            self.ui.check()
            time.sleep(.2)

    def create(self):
        self.wait('dashboard_search')
        self.tap('add_event_fab')
        self.write('note_text_field', self.text['EVENT'])
        self.tap('capture_people_button')
        self.write('event_person_search', self.person)
        self.tap('capture_create_person')
        self.tap('create_person_button')
        self.wait('person_picker', False)
        self.tap('capture_tags_button')
        self.write('capture_tag_search', self.tag)
        self.text_tap('Add #'+self.tag)
        self.tap('save_event_button')
        self.wait('add_event_sheet', False)
        self.open('EVENT')
        snapshot = self.ui.inspect()
        chip = unique_record(snapshot['elements'], 'event_contact_chip_')
        self.person_id = chip.removeprefix('event_contact_chip_')
        self.tap(chip)
        self.tap('fact_add_button')
        self.write('note_text_field', self.text['FACT'])
        self.tap('save_event_button')
        self.wait('add_event_sheet', False)
        self.home()
        self.tap('followups_shortcut')
        self.text_tap('Add a follow-up')
        self.tap('followup_choose_person')
        self.tap('event_person_option_'+self.person_id)
        self.write('followup_text', self.text['FOLLOW_UP'])
        self.tap('save_followup')
        self.wait('followup_editor', False)
        self.home()
        self.verify()
        self.note('Fictional note, fact and voluntary follow-up committed; person/tag associations verified.\n')

    def open(self, kind):
        self.tap(self.search(kind))
        self.wait({'EVENT':'event_detail_screen','FACT':'fact_reader_text','FOLLOW_UP':'followup_reader_text'}[kind])

    def verify(self):
        for kind in self.text:
            self.open(kind)
            snapshot = self.ui.inspect()
            if not any(self.text[kind] in e['text'] for e in snapshot['elements']):
                raise AssertionError(f'Saved {kind} text is missing')
            if kind == 'EVENT':
                unique_record(snapshot['elements'], 'event_contact_chip_', 'event_contact_chip_'+self.person_id)
                if not any(self.tag in e['text'] for e in snapshot['elements']):
                    raise AssertionError('Saved tag association is missing')
            else:
                target = 'fact_reader_person' if kind=='FACT' else 'followup_reader_person'
                if not any(e['id']==target and self.person in e['text'] for e in snapshot['elements']):
                    # Compose may expose button text in a child node.
                    self.step('assert', target=dict(text=self.person))
                if kind=='FOLLOW_UP':
                    self.step('assert', target=dict(text='Reopen' if self.completed else 'Mark done'))
        self.home()
        for query, prefix, expected in [
            (self.person, 'dashboard_person_', 'dashboard_person_'+self.person_id),
            (self.tag, 'dashboard_tag_', self.tag_id),
        ]:
            if any(e['id']=='clear_dashboard_search_button' for e in self.ui.inspect()['elements']):
                self.tap('clear_dashboard_search_button')
            self.write('dashboard_search', query)
            until = time.monotonic()+10
            while True:
                snapshot = self.ui.inspect()
                if any(e['id'].startswith(prefix) for e in snapshot['elements']):
                    entity = unique_record(snapshot['elements'], prefix, expected)
                    if prefix=='dashboard_tag_':
                        self.tag_id = entity
                    break
                if time.monotonic() >= until:
                    raise AssertionError('Saved association entity is missing: '+prefix)
                self.ui.check()
                time.sleep(.2)
        self.home()

    def edit(self):
        for kind in self.text:
            self.open(kind)
            self.tap({'EVENT':'edit_event_button','FACT':'fact_reader_edit','FOLLOW_UP':'followup_reader_edit'}[kind])
            self.write('followup_text' if kind=='FOLLOW_UP' else 'note_text_field', ' amended', append=True)
            self.tap('save_followup' if kind=='FOLLOW_UP' else 'save_event_edit_button')
            self.wait('followup_editor' if kind=='FOLLOW_UP' else 'add_event_sheet', False)
            self.text[kind] += ' amended'
            self.home()
        self.open('FOLLOW_UP')
        self.tap('followup_reader_complete')
        self.step('wait_for', target=dict(text='Reopen'), timeout=10)
        self.completed = True
        self.home()
        self.verify()
        self.note('Edits and follow-up completion confirmed before force-stop.\n')

    def delete(self):
        for kind in self.text:
            self.open(kind)
            if kind=='EVENT':
                self.step('tap', target=dict(description='Delete'))
                self.text_tap('Delete')
            elif kind=='FACT':
                self.tap('delete_fact_'+self.ids[kind].rsplit('_',1)[1])
            else:
                self.tap('followup_reader_delete')
            self.wait({'EVENT':'event_detail_screen','FACT':'fact_reader','FOLLOW_UP':'followup_reader'}[kind], False)
            self.home()
            self.search(kind, expect=False)
        self.note('All three records confirmed absent from active recall before force-stop.\n')

    def scroll_to(self, id):
        for _ in range(12):
            snap = self.ui.inspect()
            if any(e['id']==id and not e['clipped'] for e in snap['elements']):
                return
            self.step('swipe', direction='up')
        raise AssertionError('Could not reveal '+id)

    def verify_deleted(self):
        for kind in self.text:
            self.search(kind, expect=False)
        self.home()
        self.tap('settings_button')
        self.scroll_to('show_recently_deleted')
        self.tap('show_recently_deleted')
        for kind, record in self.ids.items():
            id='recover_'+{'EVENT':'event','FACT':'fact','FOLLOW_UP':'follow_up'}[kind]+'_'+record.rsplit('_',1)[1]
            self.scroll_to(id)
            self.tap(id)
            self.wait(id, False)
        self.home()
        self.verify()
        self.note('Recoverable deletion survived; restoring preserved original IDs, text, associations and completion without duplication.\n')

    def restart(self):
        def pid():
            value=self.device('shell', 'pidof '+PACKAGE+' || true').strip()
            if value and not re.fullmatch('[1-9][0-9]*', value):
                raise AssertionError('Unexpected app process identity: '+value)
            return value
        before=pid()
        if not before:
            raise AssertionError('App was not running before force-stop')
        self.device('shell','am','force-stop',PACKAGE)
        if pid():
            raise AssertionError('App remained alive after force-stop')
        output=self.device('shell','am','start','-W','-n',PACKAGE+'/.ui.MainActivity')
        if re.search(r'Error|Exception',output):
            raise AssertionError('App relaunch failed: '+output)
        after=pid()
        if not after or after==before:
            raise AssertionError('Relaunch did not produce a new app PID')
        self.ui.invalidate()
        self.wait('dashboard_search')
        self.note(f'Force-stop/relaunch verified: PID {before} -> {after}.\n')
        return dict(before_pid=int(before), after_pid=int(after))


def run_recovery(device, case=None, check=lambda: None, note=lambda text: None, progress=lambda result: None, capture=lambda label: None):
    journey=RecoveryJourney(device,check,note)
    journey.create()
    results=[]
    for name in CASES if case is None else (case,):
        started=time.monotonic()
        if name=='edited_records':journey.edit()
        if name=='deleted_records':journey.delete()
        result=dict(case=name,status='running')
        progress(result)
        result.update(journey.restart())
        if name=='deleted_records':journey.verify_deleted()
        else:journey.verify()
        capture(name)
        result.update(status='passed',duration_seconds=round(time.monotonic()-started,3),record_ids=dict(journey.ids))
        results.append(result)
        progress(result)
    return results
