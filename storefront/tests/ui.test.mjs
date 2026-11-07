import {test} from 'node:test';
import assert from 'node:assert/strict';
import {setBusy} from '../assets/ui.js';
test('unlocking a replaced view preserves its intentional disabled controls',()=>{
  const control={disabled:true,dataset:{}};const root={querySelectorAll:selector=>selector==='a.button'?[]:[control],setAttribute(){}};
  setBusy(root,false);assert.equal(control.disabled,true);
});
test('busy state restores each existing control to its prior state',()=>{
  const controls=[{disabled:false,dataset:{}},{disabled:true,dataset:{}}],root={querySelectorAll:selector=>selector==='a.button'?[]:controls,setAttribute(){}};
  setBusy(root,true);assert.ok(controls.every(control=>control.disabled));setBusy(root,false);
  assert.deepEqual(controls.map(control=>control.disabled),[false,true]);
});
