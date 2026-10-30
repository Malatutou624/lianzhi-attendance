// 一键生成小程序 logo 多尺寸 PNG
const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const svgPath = path.join(__dirname, '..', 'public', 'logo.svg');
const outDir = path.join(__dirname, '..', 'public');

// 微信小程序要求的图标尺寸（来自微信公众平台规范）
const sizes = [
  { name: 'logo_810',   size: 810 },   // 主图标（提交审核用）
  { name: 'logo_512',   size: 512 },   // 大尺寸通用
  { name: 'logo_192',   size: 192 },   // Android 桌面图标
  { name: 'logo_144',   size: 144 },   // iPhone
  { name: 'logo_96',    size: 96  },   // iPad / 通用 web
  { name: 'logo_72',    size: 72  },   // iPad mini
  { name: 'logo_48',    size: 48  },   // 工具栏
  { name: 'logo_36',    size: 36  },   // 小尺寸
  { name: 'favicon',    size: 64, ext: '.ico' } // favicon
];

async function generate() {
  if (!fs.existsSync(svgPath)) {
    console.error('❌ logo.svg 不存在:', svgPath);
    process.exit(1);
  }
  const svgBuf = fs.readFileSync(svgPath);
  console.log('🎨 正在生成 logo PNG 系列...\n');

  for (const { name, size, ext } of sizes) {
    const out = path.join(outDir, `${name}${ext || '.png'}`);
    try {
      await sharp(svgBuf)
        .resize(size, size)
        .png()
        .toFile(out);
      console.log(`  ✅ ${size}x${size}  →  ${path.relative(process.cwd(), out)}`);
    } catch (e) {
      console.error(`  ❌ ${name}: ${e.message}`);
    }
  }
  console.log('\n🎉 全部完成！');
}

generate();
