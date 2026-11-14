const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1500,height:900},deviceScaleFactor:2});
await p.goto('file://'+process.argv[2]);const el=await p.$('.w');await el.screenshot({path:process.argv[3]});await b.close();})();
