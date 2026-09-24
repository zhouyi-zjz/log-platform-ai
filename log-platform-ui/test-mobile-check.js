const fs = require('fs');
const path = require('path');

// 读取移动端路由文件
const mobileRoutesContent = fs.readFileSync(
  path.resolve(__dirname, 'src/router/modules/mobile.js'), 
  'utf8'
);

console.log('Mobile routes content:');
console.log(mobileRoutesContent.substring(0, 200));
console.log('...');

// 读取移动端聊天页面
try {
  const chatPageContent = fs.readFileSync(
    path.resolve(__dirname, 'src/views/mobile/chat/index.vue'), 
    'utf8'
  );
  console.log('\nChat page loaded successfully, size:', chatPageContent.length, 'characters');
} catch (e) {
  console.error('\nError loading chat page:', e.message);
}

// 检查SSO回调页面
try {
  const ssoPageContent = fs.readFileSync(
    path.resolve(__dirname, 'src/views/mobile/sso/callback.vue'), 
    'utf8'
  );
  console.log('\nSSO callback page loaded successfully, size:', ssoPageContent.length, 'characters');
} catch (e) {
  console.error('\nError loading SSO callback page:', e.message);
}