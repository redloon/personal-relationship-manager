"""SDK-free probes for batched Android interactions."""
import importlib.util
from pathlib import Path
import shlex
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('interaction', Path(__file__).with_name('interaction.py'))
interaction = importlib.util.module_from_spec(spec)
spec.loader.exec_module(interaction)

XML = '''<?xml version="1.0"?><hierarchy rotation="0"><node package="com.example" class="android.view.View" bounds="[0,0][320,640]">
<node resource-id="com.example:id/search" text="Search" content-desc="Find context" class="android.widget.EditText" bounds="[16,40][304,88]" enabled="true" clickable="true"/>
<node resource-id="save" text="Save" class="android.widget.Button" bounds="[220,100][304,145]" enabled="true" clickable="true"/>
<node text="Disabled" bounds="[0,180][100,230]" enabled="false" clickable="true"/>
<node text="Offscreen" bounds="[0,650][100,690]" enabled="true" clickable="true"/>
</node></hierarchy>UI hierarchy dumped to: /dev/tty'''

class Device:
    def __init__(self, xml=XML):
        self.xml = xml
        self.calls = []
    def __call__(self, *args):
        self.calls.append(args)
        if args == ('exec-out', 'uiautomator', 'dump', '/dev/tty'):
            return self.xml
        if args == ('shell', 'wm', 'size'):
            return 'Physical size: 320x640\n'
        return ''

class InteractionTests(unittest.TestCase):
    def setUp(self):
        self.device = Device()
        self.check = Mock()
        self.ui = interaction.Interaction(self.device, self.check)

    def test_inspect_parses_noise_and_stable_ids(self):
        result = self.ui.inspect()
        self.assertEqual(result['screen'], {'width': 320, 'height': 640})
        field = next(e for e in result['elements'] if e['text'] == 'Search')
        self.assertEqual(field['id'], 'search')
        self.assertEqual(field['description'], 'Find context')
        self.assertEqual(field['bounds'], [16, 40, 304, 88])
        self.assertTrue(field['enabled'])
        self.check.assert_called()

    def test_selector_is_resolved_at_action_time(self):
        self.ui.inspect()
        self.device.xml = XML.replace('[220,100][304,145]', '[20,300][104,345]')
        self.ui.perform({'action':'tap', 'target':{'id':'save'}})
        self.assertIn(('shell','input','tap','62','322'), self.device.calls)

    def test_ref_expires_after_action_and_after_new_inspection(self):
        initial = self.ui.inspect()
        field = next(e for e in initial['elements'] if e['text']=='Search')
        step = {'action':'tap','target':{'ref':field['ref']},'snapshot_id':initial['snapshot_id']}
        self.ui.perform(step)
        with self.assertRaisesRegex(ValueError,'snapshot'):
            self.ui.perform(step)
        self.ui.inspect()
        with self.assertRaisesRegex(ValueError,'snapshot'):
            self.ui.perform(step)

    def test_failed_inspection_invalidates_previous_refs(self):
        snap = self.ui.inspect()
        self.device.xml = 'UI never settled'
        with self.assertRaises(ValueError):
            self.ui.inspect()
        with self.assertRaisesRegex(ValueError,'snapshot'):
            self.ui.perform({'action':'tap','target':{'ref':'e1'},'snapshot_id':snap['snapshot_id']})

    def test_coordinates_require_snapshot_and_reject_outside_screen(self):
        with self.assertRaisesRegex(ValueError,'snapshot'):
            self.ui.perform({'action':'tap','target':{'x':20,'y':30}})
        snap = self.ui.inspect()
        with self.assertRaisesRegex(ValueError,'screen'):
            self.ui.perform({'action':'tap','target':{'x':320,'y':30},'snapshot_id':snap['snapshot_id']})

    def test_disabled_and_offscreen_refused_before_input(self):
        for text in ['Disabled','Offscreen']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                self.ui.perform({'action':'tap','target':{'text':text}})
        self.assertFalse(any(args[:2] == ('shell','input') for args in self.device.calls))

    def test_ambiguous_selector_refused(self):
        self.device.xml = XML.replace('</node></hierarchy>', '<node text="Save" bounds="[0,250][100,300]" enabled="true"/></node></hierarchy>')
        with self.assertRaisesRegex(ValueError,'ambiguous'):
            self.ui.perform({'action':'tap','target':{'text':'Save'}})

    def test_ancestor_clipping_and_keyboard_obscuring_refused(self):
        self.device.xml = '''<hierarchy><node bounds="[0,0][320,300]"><node text="Clipped" bounds="[0,280][100,350]" enabled="true"/></node><node package="com.android.inputmethod.latin" bounds="[0,200][320,640]"/></hierarchy>'''
        with self.assertRaisesRegex(ValueError,'clipped|keyboard'):
            self.ui.perform({'action':'tap','target':{'text':'Clipped'}})

    def test_virtual_non_scrollable_parent_does_not_clip_visible_footer(self):
        # Real Compose modal tree: virtual parent ends at y=581 while the
        # visible, clickable footer is drawn below it at y=584..632.
        self.device.xml = '<hierarchy><node bounds="[0,59][320,581]"><node resource-id="save_followup" bounds="[223,584][304,632]" enabled="true" clickable="true"/></node></hierarchy>'
        self.ui.perform({'action':'tap','target':{'id':'save_followup'}})
        self.assertIn(('shell','input','tap','263','608'), self.device.calls)

    def test_scrollable_container_still_clips_offscreen_child(self):
        self.device.xml = '<hierarchy><node bounds="[0,0][320,300]" scrollable="true"><node resource-id="save" bounds="[220,280][304,345]" enabled="true" clickable="true"/></node></hierarchy>'
        with self.assertRaisesRegex(ValueError,'clipped'):
            self.ui.perform({'action':'tap','target':{'id':'save'}})
        self.assertFalse(any(a[:2]==('shell','input') for a in self.device.calls))

    def test_text_shell_quoting_preserves_punctuation_and_percent_s(self):
        text = 'Mira\'s "kite" %s $(id); `id` \\ $HOME'
        self.ui.perform({'action':'type','text':text})
        commands = [a[-1] for a in self.device.calls if a[:3]==('shell','input','text')]
        chunks = [shlex.split(c)[0] for c in commands]
        self.assertEqual(''.join(c.replace('%s',' ') for c in chunks), text)
        self.assertNotIn('café', ''.join(commands))

    def test_entire_batch_validated_before_unicode_and_newline(self):
        for text in ['café','hello\nworld','🦦']:
            with self.subTest(text=text), self.assertRaisesRegex(ValueError,'ASCII|newline'):
                interaction.validate_steps([{'action':'tap','target':{'id':'save'}},{'action':'type','text':text}])
        self.assertEqual(self.device.calls, [])

    def test_schema_limits_and_unknown_keys_rejected(self):
        for steps in [[],[{'action':'key','key':'back'}]*21,[{'action':'shell','command':'id'}],[{'action':'tap','target':{'id':'save'},'typo':True}],[{'action':'key','key':'back','times':True}]]:
            with self.subTest(steps=steps), self.assertRaises(ValueError):
                interaction.validate_steps(steps)

    def test_assert_missing_and_wait_timeout(self):
        self.ui.perform({'action':'assert','target':{'text':'Absent'},'exists':False})
        with patch.object(interaction.time,'monotonic',side_effect=[0,0,1,2]), patch.object(interaction.time,'sleep'):
            with self.assertRaisesRegex(TimeoutError,'waiting'):
                self.ui.perform({'action':'wait_for','target':{'text':'Absent'},'timeout':1})

    def test_wait_checks_cancellation_between_probes(self):
        self.check.side_effect = RuntimeError('cancelled')
        with self.assertRaisesRegex(RuntimeError,'cancelled'):
            self.ui.perform({'action':'wait_for','target':{'text':'Absent'}})
        self.assertEqual(self.device.calls, [])

    def test_assert_and_wait_return_verified_not_tap_success(self):
        self.assertTrue(self.ui.perform({'action':'assert','target':{'id':'save'}})['verified'])
        self.assertTrue(self.ui.perform({'action':'wait_for','target':{'id':'save'}})['verified'])
        self.assertFalse(self.ui.perform({'action':'tap','target':{'id':'save'}})['verified'])

    def test_key_and_swipe_are_bounded(self):
        self.ui.perform({'action':'key','key':'back','times':2})
        self.assertIn(('shell','input','keyevent','4','4'),self.device.calls)
        self.ui.perform({'action':'swipe','direction':'up','duration_ms':300})
        self.assertIn(('shell','input','swipe','160','480','160','160','300'),self.device.calls)

    def test_xml_entity_declarations_rejected(self):
        self.device.xml='<!DOCTYPE x [<!ENTITY a "expanded">]><hierarchy><node text="&a;"/></hierarchy>'
        with self.assertRaisesRegex(ValueError,'XML'):
            self.ui.inspect()

if __name__ == '__main__':
    unittest.main()
