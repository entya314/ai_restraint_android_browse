const {chromium} = require('playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');
const guard = fs.readFileSync(path.join(__dirname,'../app/src/main/assets/guard.js'),'utf8').replace('__PINNED_ORIGIN__',JSON.stringify('https://shop.example.com'));
(async () => {
 const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const context = await browser.newContext();
 const calls=[]; let navigations=[];
 await context.exposeFunction('recordBridge', data => calls.push(JSON.parse(data)));
 await context.addInitScript(() => { window.RestrictedNavigation={postMessage:s=>window.recordBridge(s)}; });
 await context.addInitScript(guard);
 await context.route('**/*', async route => {
   const request=route.request();
   if(request.isNavigationRequest()) navigations.push(request.url());
   await route.fulfill({contentType:'text/html',body:`<!doctype html><html><body>
   <form id="order" action="/order" method="post">
   <input name="action" value="order"><input name="method" value="submit"><input id="name" name="name"><textarea id="notes" name="notes"></textarea><input id="people" name="people" type="number">
   <input id="labelOnly" name="label" placeholder="検索"><input id="ordinarySearchName" name="search" aria-label="検索">
   <input id="realSearch" type="search" name="q"><input id="box" role="searchbox" name="q2">
   <div role="search"><input id="roleSearch" name="q3"><div id="contentSearch" contenteditable="true"></div></div>
   <search><input id="tagSearch" name="q4"></search><button id="submit" name="commit" value="yes">注文</button></form>
   <a id="same" href="/next">次へ</a><a id="external" href="https://other.example.com/">外部</a>
   <a id="popup" href="/next" target="_blank">別窓</a><a id="intent" href="intent://launch">アプリ</a>
   </body></html>`});
 });
 const page=await context.newPage();
 await page.goto('https://shop.example.com/start');
 let passed=0;
 async function test(name, fn) { await fn(); passed++; console.log(`PASS ${name}`); }
 await test('普通の名前・備考・人数と「検索」文言だけの欄は編集できる',async()=>{
   await page.fill('#name','山田'); await page.fill('#notes','ねぎ抜き\nお願いします'); await page.fill('#people','3');
   await page.fill('#labelOnly','通常入力'); await page.fill('#ordinarySearchName','通常入力2');
   assert.equal(await page.inputValue('#name'),'山田'); assert.equal(await page.inputValue('#people'),'3');
 });
 await test('type=search、searchbox、search role内、search要素内を無効化',async()=>{
   for(const id of ['realSearch','box','roleSearch','tagSearch']) assert.equal(await page.isDisabled('#'+id),true,id);
   assert.equal(await page.getAttribute('#contentSearch','contenteditable'),'false');
 });
 await test('動的追加と属性変更に追従し、検索指定解除後は普通の入力に戻す',async()=>{
   await page.evaluate(()=>{let e=document.createElement('input');e.id='dynamic';e.type='search';document.body.append(e);});
   await page.waitForFunction(()=>document.querySelector('#dynamic').disabled);
   await page.evaluate(()=>document.querySelector('#labelOnly').setAttribute('role','searchbox'));
   await page.waitForFunction(()=>document.querySelector('#labelOnly').disabled);
   await page.evaluate(()=>document.querySelector('#labelOnly').removeAttribute('role'));
   await page.waitForFunction(()=>!document.querySelector('#labelOnly').disabled);
   await page.fill('#labelOnly','復帰');
 });
 await test('Shadow DOM内に追加した明示的検索欄も禁止',async()=>{
   await page.evaluate(()=>{const h=document.createElement('div');h.id='shadow';document.body.append(h);h.attachShadow({mode:'open'}).innerHTML='<input type=search id=s>'});
   await page.waitForFunction(()=>document.querySelector('#shadow').shadowRoot.querySelector('input').disabled);
 });
 await test('通常POSTは名前・備考・人数・submitterを保持してネイティブ仲介し、検索欄は送信しない',async()=>{
   calls.length=0; await page.click('#submit');
   await page.waitForTimeout(100);
   const m=calls.find(m=>m.kind==='form'); assert.ok(m); assert.equal(m.method,'post');
   assert.deepEqual(m.fields.filter(f=>['name','people','commit'].includes(f[0])),[['name','山田'],['people','3'],['commit','yes']]);
   assert.ok(m.fields.some(f=>f[0]==='notes' && f[1]==='ねぎ抜き\r\nお願いします'));
   assert.equal(m.fields.some(f=>/^q[234]?$/.test(f[0])),false);
 });
 await test('SPA側でpreventDefaultした送信を二重送信しない',async()=>{
   calls.length=0; await page.evaluate(()=>document.querySelector('#order').addEventListener('submit', e=>e.preventDefault(),{once:true}));
   await page.click('#submit'); await page.waitForTimeout(80); assert.equal(calls.some(m=>m.kind==='form'),false);
 });
 await test('form.submit()もネイティブに仲介',async()=>{
   calls.length=0; await page.evaluate(()=>HTMLFormElement.prototype.submit.call(document.querySelector('#order'))); await page.waitForTimeout(80);
   assert.ok(calls.some(m=>m.kind==='form'));
 });
 await test('外部formaction、外部POST action、外部リンク、intent、別窓は禁止',async()=>{
   for(const mode of ['action','formaction']) {
     calls.length=0; await page.evaluate(mode=>{(mode==='action'?document.querySelector('#order'):document.querySelector('#submit')).setAttribute(mode,'https://evil.com/order');},mode);
     await page.click('#submit'); await page.waitForTimeout(50); assert.equal(calls.some(m=>m.kind==='form'),false); assert.ok(calls.some(m=>m.kind==='blocked'));
     await page.evaluate(mode=>(mode==='action'?document.querySelector('#order'):document.querySelector('#submit')).setAttribute(mode,mode==='action'?'/order':''),mode);
   }
   for(const id of ['external','intent','popup']) {const before=page.url(); await page.click('#'+id); assert.equal(page.url(),before);}
   assert.equal(await page.evaluate(()=>window.open('https://evil.com')),null);
   assert.equal(context.pages().length,1);
 });
 await test('form属性で外部に紐付く検索欄も同じタスク内のsubmit前に除外',async()=>{
   calls.length=0;
   await page.evaluate(()=>{
     const e=document.createElement('input');e.type='search';e.name='outsideQuery';e.value='blocked';e.setAttribute('form','order');document.body.append(e);
     document.querySelector('#order').setAttribute('action','/order');document.querySelector('#submit').removeAttribute('formaction');
     HTMLFormElement.prototype.submit.call(document.querySelector('#order'));
   });
   await page.waitForTimeout(80);
   const m=calls.find(m=>m.kind==='form');assert.ok(m);assert.equal(m.fields.some(f=>f[0]==='outsideQuery'),false);
 });
 await test('同じホストへのリンク移動は維持',async()=>{
   await page.click('#same'); await page.waitForURL('https://shop.example.com/next');
 });
 console.log(`${passed} browser tests passed; Chromium ${browser.version()}`);
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
