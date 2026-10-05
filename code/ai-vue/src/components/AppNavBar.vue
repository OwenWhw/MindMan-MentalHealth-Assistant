<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Menu, X } from 'lucide-vue-next'

const props = defineProps({
  brandName: { type: String, default: 'MindMan' },
  actions: { type: Array, default: () => [] },
  currentPath: { type: String, default: '' }
})
const emit = defineEmits(['action'])
const router = useRouter()
const mobileOpen = ref(false)
function isActive(action) {
  return action.path === props.currentPath || (action.path === '/home/articles' && props.currentPath.startsWith('/home/articles/'))
}
function handleAction(action) {
  if (action.path && action.path !== props.currentPath) router.push(action.path)
  emit('action', action)
  mobileOpen.value = false
}
</script>

<template>
  <header class="app-nav">
    <div class="nav-shell">
      <slot name="brand">
        <router-link to="/home" class="nav-brand" aria-label="MindMan 首页">
          <img class="nav-brand-mark" src="/mindman-mark-refresh.svg" alt="" aria-hidden="true" />
          <span class="nav-brand-copy"><strong>{{ brandName }}</strong></span>
        </router-link>
      </slot>
      <slot name="extra" />
      <nav class="nav-actions" :class="{ open: mobileOpen }" aria-label="页面导航">
        <button v-for="action in actions" :key="action.key" type="button" class="nav-action" :class="{ active: isActive(action) }" :aria-label="action.title" :aria-current="isActive(action) ? 'page' : undefined" @click="handleAction(action)"><el-icon><component :is="action.icon" /></el-icon><span>{{ action.title }}</span></button>
      </nav>
      <button class="nav-toggle" type="button" :aria-expanded="mobileOpen" aria-label="切换导航" @click="mobileOpen = !mobileOpen"><X v-if="mobileOpen" :size="19" /><Menu v-else :size="19" /></button>
      <div class="nav-after"><slot name="actions-after" /></div>
    </div>
  </header>
</template>

<style scoped>
.app-nav{position:fixed;top:0;left:0;right:0;z-index:100;background:#f8f7f2ee;backdrop-filter:blur(18px);-webkit-backdrop-filter:blur(18px);border-bottom:1px solid #e5e9df}.nav-shell{position:relative;max-width:1400px;height:82px;margin:auto;padding:0 24px;display:flex;align-items:center;gap:24px}.nav-brand{display:flex;align-items:center;color:#23342d;min-width:150px;text-decoration:none}.nav-brand strong{display:block;font:600 20px Georgia,serif;letter-spacing:-.8px;line-height:1.1}.nav-brand small{display:block;color:#879087;font-size:10px;letter-spacing:1.2px;margin-top:3px}.nav-actions{display:flex;align-items:center;justify-content:center;gap:22px;margin-left:auto;min-width:0}.nav-action{position:relative;border:0;background:transparent;cursor:pointer;border-radius:8px;padding:9px 2px;color:#6f8072;display:flex;align-items:center;gap:6px;font-size:13px;font-weight:600;white-space:nowrap;transition:color .2s,background .2s}.nav-action .el-icon{display:none}.nav-action:hover{color:#315d3f}.nav-action.active{color:#315d3f;font-weight:700}.nav-action.active:after{content:'';position:absolute;left:50%;bottom:-10px;width:16px;height:2px;background:#829e77;border-radius:2px;transform:translateX(-50%)}.nav-after{flex:none;display:flex;align-items:center;padding-left:15px;border-left:1px solid #dfe7da}.nav-shell>:slotted(.nav-progress),.nav-shell>:slotted(.nav-search){flex:1;min-width:120px;max-width:250px}.nav-seal{flex:none;width:40px;height:40px;display:grid;place-items:center;border-radius:13px;box-shadow:0 6px 17px #2d513c1b;transition:transform .25s,box-shadow .25s}.nav-seal img{display:block;width:100%;height:100%}.nav-seal:hover{transform:rotate(-6deg) translateY(-2px);box-shadow:0 8px 18px #2d513c2d}.nav-seal:focus-visible{outline:2px solid #315b3d;outline-offset:3px}.nav-toggle{display:none;flex:none;width:36px;height:36px;align-items:center;justify-content:center;border:1px solid #dce7da;border-radius:9px;background:#fffdf9;color:#426b4b;cursor:pointer}.nav-toggle:focus-visible{outline:2px solid #719575;outline-offset:2px}
@media(max-width:1120px){.nav-shell{gap:15px;padding:0 23px}.nav-actions{gap:14px}.nav-action{font-size:12px}.nav-brand{min-width:132px}.nav-shell>:slotted(.nav-progress),.nav-shell>:slotted(.nav-search){max-width:190px}}
@media(max-width:900px){.nav-shell{height:74px;padding:0 20px;gap:14px}.nav-actions{display:none}.nav-actions.open{position:absolute;top:calc(100% - 1px);left:0;right:0;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:7px;padding:14px 20px 17px;background:#f8f7f2f7;border:1px solid #e5e9df;border-top:0;box-shadow:0 16px 26px #30483414}.nav-action{justify-content:center;padding:10px 8px;border-radius:8px;background:#fffdf9}.nav-action .el-icon{display:inline-flex;font-size:15px}.nav-action.active:after{display:none}.nav-toggle{display:flex}.nav-after{margin-left:auto}.nav-shell>:slotted(.nav-progress),.nav-shell>:slotted(.nav-search){min-width:105px;max-width:230px}}
@media(max-width:620px){.nav-shell{height:70px;padding:0 14px;gap:8px}.nav-brand{min-width:0}.nav-brand small{display:none}.nav-brand strong{font-size:18px}.nav-after{padding-left:8px}.nav-shell>:slotted(.nav-progress){display:none}.nav-shell>:slotted(.nav-search){min-width:80px;flex:1;max-width:none}.nav-toggle{width:34px;height:34px}.nav-seal{width:34px;height:34px;border-radius:10px}.nav-actions.open{grid-template-columns:repeat(2,minmax(0,1fr));padding:12px 14px}.nav-action{font-size:12px}}
.nav-shell{padding-right:24px;padding-left:24px}
.nav-brand{gap:10px;min-width:150px}
.nav-brand-mark{width:38px;height:38px;flex:none;border-radius:12px;box-shadow:0 5px 14px #2d513c1b}
.nav-brand-copy{display:block}
.nav-brand small{letter-spacing:.08em}
.nav-actions{justify-content:flex-end}
@media(max-width:1120px){.nav-shell{padding-right:18px;padding-left:18px}.nav-brand{min-width:145px}}
@media(max-width:900px){.nav-shell{padding-right:18px;padding-left:18px}}
@media(max-width:620px){.nav-shell{padding-right:12px;padding-left:12px}.nav-brand{gap:7px}.nav-brand-mark{width:34px;height:34px;border-radius:10px}.nav-brand strong{font-size:18px}.nav-seal{display:none}}
</style>
