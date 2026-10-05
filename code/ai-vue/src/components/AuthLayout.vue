<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowUpRight, Heart, Leaf, Sparkles } from 'lucide-vue-next'
import LoginForm from './LoginForm.vue'
import RegisterForm from './RegisterForm.vue'

const route = useRoute()
const panel = ref(route.query.panel === 'register' ? 'register' : 'login')
const pageRef = ref(null)
let motionContext
watch(() => route.query.panel, (value) => {
  if (value === 'login' || value === 'register') panel.value = value
})
onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  import('gsap').then(({ gsap }) => {
    if (!pageRef.value) return
    motionContext = gsap.context(() => {
      gsap.from('.story-content > *', { y: 20, autoAlpha: 0, duration: 0.8, stagger: 0.09, ease: 'power3.out', clearProps: 'all' })
      gsap.from('.story-script', { y: 26, autoAlpha: 0, duration: 1, delay: 0.38, ease: 'power3.out', clearProps: 'all' })
      gsap.from('.form-inner', { y: 20, autoAlpha: 0, duration: 0.8, delay: 0.15, ease: 'power2.out', clearProps: 'all' })
    }, pageRef.value)
  })
})
onUnmounted(() => motionContext?.revert())
</script>

<template>
  <main ref="pageRef" class="auth-layout">
    <div class="auth-shell">
      <section class="story-panel" aria-label="MindMan 介绍">
        <div class="story-top"><span class="brand-icon"><img src="/mindman-mark-refresh.svg" alt="MindMan 标识" /></span><div><strong>MindMan</strong></div></div>
        <div class="story-content"><span class="eyebrow"><span class="eyebrow-dot"></span> 给心一个慢下来的地方</span><h1>让每一种情绪，<br><em>都有被听见的地方。</em></h1><p>在这里，不需要急着变好。慢慢说，慢慢感受，我们陪你走过每一个当下。</p><div class="story-points"><span><Heart :size="16" />安心表达</span><span><Leaf :size="16" />觉察自己</span><span><Sparkles :size="16" />温柔成长</span></div></div>
        <div class="story-art" aria-hidden="true"><div class="art-halo"></div><div class="art-sun"></div><div class="art-floor"></div><div class="art-vase"></div><div class="art-stem stem-a"></div><div class="art-stem stem-b"></div><div class="art-stem stem-c"></div><div class="art-leaf leaf-a"></div><div class="art-leaf leaf-b"></div><div class="art-leaf leaf-c"></div><div class="art-flower"></div></div>
        <div class="story-script" aria-hidden="true">take your time<span>I'm listening.</span></div>
        <div class="story-foot">慢慢来，你已经做得很好。</div>
      </section>
      <section class="form-panel" aria-label="账号入口"><div class="form-top"><span>欢迎来到 MindMan</span></div><div class="form-inner"><span class="form-kicker">{{ panel === 'login' ? '欢迎回来' : '开启新的旅程' }}</span><LoginForm v-if="panel === 'login'" @switch="panel = 'register'" /><RegisterForm v-else @switch="panel = 'login'" /></div><div class="form-foot"><span>让心慢下来，日子会慢慢明亮。</span><ArrowUpRight :size="16" /></div></section>
    </div>
  </main>
</template>

<style scoped>
.auth-layout{min-height:100vh;background:#f5f4ed;padding:22px;display:flex;align-items:center;justify-content:center;color:#293e30}.auth-shell{width:min(1220px,100%);min-height:min(780px,calc(100vh - 44px));display:grid;grid-template-columns:1.1fr .9fr;background:#fff;border:1px solid #e9e8df;border-radius:23px;overflow:hidden;box-shadow:0 26px 70px #41574416}.story-panel{background:#e7eee2;position:relative;overflow:hidden;min-height:690px;padding:38px 48px;display:flex;flex-direction:column}.story-top{display:flex;align-items:center;gap:11px;z-index:2}.brand-icon{width:42px;height:42px;background:#d4e3cf;border-radius:13px;display:grid;place-items:center;color:#466c4e}.story-top strong{font:600 21px Georgia,serif;letter-spacing:-.5px;display:block}.story-top small{display:block;font-size:10px;color:#8c9b8c;letter-spacing:1px;margin-top:3px}.story-content{position:relative;z-index:2;margin-top:100px;max-width:510px}.eyebrow,.form-kicker{font-size:11px;font-weight:700;letter-spacing:2px;color:#648469}.eyebrow{display:flex;align-items:center;gap:8px}.eyebrow-dot{width:7px;height:7px;background:#7e9f77;border-radius:50%}.story-content h1{font:600 clamp(32px,3vw,47px)/1.5 'Noto Serif SC',Georgia,serif;letter-spacing:-1.3px;margin:21px 0;color:#2d4635}.story-content h1 em{color:#6f906e;font-style:normal}.story-content p{font-size:14px;color:#758a78;line-height:1.9;max-width:440px}.story-points{display:flex;gap:20px;margin-top:30px;color:#52765b;font-size:12px}.story-points span{display:flex;align-items:center;gap:6px}.story-art{position:absolute;right:0;bottom:0;width:82%;height:45%;pointer-events:none}.art-halo{position:absolute;right:5%;bottom:0;width:72%;height:100%;border-radius:50% 50% 0 0;background:#dbe7d5;box-shadow:inset 20px 15px 45px #cbdcc5}.art-sun{position:absolute;right:26%;top:7%;width:125px;height:125px;border-radius:50%;background:#f8eccc;box-shadow:0 0 60px #fff5d2}.art-floor{position:absolute;left:15%;right:0;bottom:35px;height:18px;background:#bdcdae;transform:skewY(-2deg)}.art-vase{position:absolute;right:29%;bottom:49px;width:125px;height:140px;border-radius:35% 35% 44% 44% / 14% 14% 55% 55%;background:linear-gradient(100deg,#d7c5af,#e7d8c4 45%,#bfa98e);box-shadow:20px 20px 25px #81987855;z-index:3}.art-vase:before{content:'';position:absolute;width:60px;height:14px;top:-7px;left:33px;background:#9e8e7c;border-radius:50%}.art-stem{position:absolute;right:41%;bottom:182px;background:#587953;width:3px;height:160px;transform-origin:bottom;z-index:2;border-radius:3px}.stem-a{transform:rotate(-18deg)}.stem-b{height:184px;transform:rotate(15deg)}.stem-c{height:137px;transform:rotate(41deg)}.art-leaf{position:absolute;width:51px;height:20px;background:#78996f;border-radius:100% 0 100% 0;z-index:3}.leaf-a{right:43%;bottom:270px;transform:rotate(20deg)}.leaf-b{right:31%;bottom:290px;transform:rotate(-24deg)}.leaf-c{right:44%;bottom:230px;transform:rotate(30deg)}.art-flower{position:absolute;right:25%;bottom:336px;width:47px;height:47px;background:#e9b695;border-radius:65% 45% 60% 45%;transform:rotate(25deg);box-shadow:-20px 4px 0 -5px #f4cbae,14px -11px 0 -7px #f3c5a3;z-index:4}.story-foot{margin-top:auto;color:#92a293;font:italic 10px Georgia,serif;letter-spacing:1.7px;z-index:4}.form-panel{display:flex;flex-direction:column;padding:39px 48px;background:#fff}.form-top,.form-foot{display:flex;justify-content:space-between;align-items:center;color:#9aab9c;font-size:11px}.form-inner{width:min(380px,100%);margin:auto}.form-kicker{display:block;margin-bottom:15px}.form-foot{border-top:1px solid #f0f1e9;padding-top:19px}.form-panel :deep(.auth-title){font-family:'Noto Serif SC',serif;color:#2d4635;font-size:29px;letter-spacing:-.5px}.form-panel :deep(.auth-subtitle){color:#9aa89c}.form-panel :deep(.el-input__wrapper){box-shadow:0 0 0 1px #e0e8dc inset;background:#fafbf7;border-radius:9px;min-height:46px}.form-panel :deep(.el-input__wrapper.is-focus){box-shadow:0 0 0 1px #6a8d6c inset}.form-panel :deep(.auth-submit){background:#31543e;border-radius:8px;box-shadow:none;letter-spacing:.5px}.form-panel :deep(.auth-submit:hover){background:#244530}.form-panel :deep(.auth-switch a){color:#54785b;cursor:pointer}.form-panel :deep(.auth-preview-tip){background:#edf4e8;color:#4b7154}.form-panel :deep(.el-form-item__label){color:#607567}
@media(max-width:900px){.auth-layout{padding:0}.auth-shell{border:0;border-radius:0;min-height:100vh;grid-template-columns:1fr}.story-panel{min-height:360px;padding:30px}.story-content{margin-top:48px}.story-content h1{font-size:31px}.story-art{width:48%;height:75%;opacity:.5}.story-points,.story-foot{display:none}.form-panel{padding:34px 30px}.form-inner{margin:28px auto 40px}}
@media(max-width:520px){.story-panel{min-height:300px}.story-content{margin-top:35px}.story-content h1{font-size:27px}.story-content p{font-size:12px;max-width:250px}.story-art{width:55%;height:65%;opacity:.35}.form-panel{padding:28px 24px}.form-inner{width:100%}}
.auth-layout{background:#faf9f5}.auth-shell{border-radius:6px;box-shadow:0 24px 60px #42564012}.story-panel{background:#e8eee6}.story-content{margin-top:86px}.story-content h1{font-size:clamp(39px,3.5vw,55px);font-weight:500;line-height:1.4;letter-spacing:-.055em;text-wrap:balance}.story-content h1 em{color:#5b8061}.story-content p{max-width:390px;color:#627e69;line-height:2}.eyebrow{letter-spacing:.19em}.story-script{position:absolute;z-index:5;bottom:82px;left:44px;color:#fffdf4;font:italic 500 clamp(56px,6.2vw,91px)/.82 Georgia,serif;letter-spacing:-.09em;text-shadow:0 5px 25px #3356404d;pointer-events:none}.story-script span{display:block;padding-left:58px;font-size:.54em;line-height:1.25}.story-foot{color:#59765d}.form-panel{padding:40px 56px}.form-panel :deep(.auth-title){font-size:35px;font-weight:500;letter-spacing:-.055em}.form-panel :deep(.auth-submit){border-radius:3px;min-height:48px}.form-panel :deep(.auth-submit:active){transform:translateY(1px)}
.brand-icon img{width:100%;height:100%;display:block}
@media(max-width:900px){.auth-shell{grid-template-rows:auto 1fr;min-height:100dvh}.story-content{margin-top:45px}.story-content h1{font-size:35px}.story-script{bottom:20px;left:auto;right:28px;font-size:54px}.form-panel{padding:34px 30px}}
@media(max-width:520px){.story-panel{min-height:320px}.story-content{margin-top:32px}.story-content h1{font-size:29px}.story-content p{max-width:270px;font-size:12px}.story-points{gap:9px;font-size:10px}.story-script{display:none}.form-panel{padding:28px 24px}}
.story-content .eyebrow,.form-kicker{letter-spacing:.08em}
.story-script{font-family:'Noto Serif SC',Georgia,serif;font-style:normal;line-height:1.08;letter-spacing:-.08em}
.story-script span{padding-left:2.1em;line-height:1.25}
.story-foot{font:500 11px/1.5 'Noto Sans SC',sans-serif;letter-spacing:.08em}
</style>
