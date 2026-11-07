import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../assets/js/admin.js',import.meta.url),'utf8');
function cachedAdmin(){
  const events=new Map(),root={hidden:false};let reloads=0;
  const context={window:{addEventListener:(name,fn)=>events.set(name,fn)},document:{documentElement:root,addEventListener(){}},location:{reload(){reloads++;}}};
  vm.runInNewContext(source,context);
  return {events,root,reloads:()=>reloads};
}
test('leaving admin masks sensitive DOM before browser caches the document',()=>{
  const page=cachedAdmin();page.events.get('pagehide')?.({persisted:true});assert.equal(page.root.hidden,true);
});
test('Back/Forward cache restore reloads authorization before revealing previous admin DOM',()=>{
  const page=cachedAdmin();page.events.get('pageshow')?.({persisted:true});assert.equal(page.reloads(),1);assert.equal(page.root.hidden,true);
});
test('a normal fresh load does not cause a reload loop',()=>{
  const page=cachedAdmin();page.events.get('pageshow')?.({persisted:false});assert.equal(page.reloads(),0);
});
