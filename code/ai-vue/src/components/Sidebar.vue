<template>
    <el-aside :width="isCollapse ? '64px' : '240px'" class="sidebar-aside">
        <el-menu
        :collapse="isCollapse"
        :default-active="route.path"
        class="menu-style"
      >
        <div class="brand">
            <el-image class="brand-logo" :src="iconUrl" alt='logo' />
            <div class="info-card" :class="{ 'is-hidden': isCollapse }">
                <h1 class="brand-title"> AI助手</h1>
                <p class="brand-subtitle">管理后台</p>
            </div>
        </div>
        <el-menu-item @click="selectMenu" v-for="item in router.options.routes[0].children" :key="item.path" :index="`/back/${item.path}`">
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <template #title><span>{{ item.meta.title }}</span></template>
        </el-menu-item>

      </el-menu>
    </el-aside>
</template>
<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useLayoutStore } from '@/stores/layout.js'
const router = useRouter()
const route = useRoute()

const iconUrl = new URL('@/assets/images/mascot.jpg', import.meta.url).href

const isCollapse = computed(() => useLayoutStore().isCollapse)

const selectMenu = (key) => {
    router.push(key.index)
  }


</script>
<style lang="scss" scoped>
.sidebar-aside {
    transition: width 0.28s ease;
    overflow: hidden;
}
.menu-style {
  height: 100%;
  border-right: none;
  background: linear-gradient(180deg, #2b2450 0%, #342b5e 55%, #3d3168 100%);

  .brand {
      display: flex;
      align-items: center;
      /* 左对齐 + 固定布局宽度：宽度动画期间品牌区不回流，仅被 aside 裁切 */
      justify-content: flex-start;
      min-width: 240px;
      white-space: nowrap;
      padding: 20px 10px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);

      .brand-logo {
          width: 46px;
          height: 46px;
          margin-right: 10px;
          flex-shrink: 0;
          border-radius: 50%;
      }

      .info-card {
          transition: opacity 0.2s ease, visibility 0.2s ease;

          /* 折叠时文字淡出而非瞬间消失，避免挤压感 */
          &.is-hidden {
              opacity: 0;
              visibility: hidden;
          }

          .brand-title {
              font-size: 18px;
              font-weight: bold;
              margin-bottom: 2px;
              color: #fff;
            }
            .brand-subtitle {
              font-size: 12px;
              color: rgba(255, 255, 255, 0.55);
              letter-spacing: 2px;
            }
      }
  }

  :deep(.el-menu-item) {
      margin: 6px 12px;
      border-radius: 10px;
      color: rgba(255, 255, 255, 0.65);
      height: 46px;
      transition: all 0.2s;

      .el-icon {
          color: inherit;
      }

      &:hover {
          background: rgba(255, 255, 255, 0.08);
          color: #fff;
      }

      &.is-active {
          background: linear-gradient(135deg, #6a52c9 0%, #8a6fd8 100%);
          color: #fff;
          box-shadow: 0 4px 12px rgba(106, 82, 201, 0.45);
      }
  }

  /* 折叠态：保持菜单 240px 布局宽度，由外层 aside 裁切呈现，避免渐变背景瞬间消失；
     菜单项通栏铺满 64px 导轨，图标随 margin 过渡平滑滑至正中 */
  &.el-menu--collapse {
      width: 240px;

      :deep(.el-menu-item) {
          margin: 6px 0;
      }
  }
}
</style>
