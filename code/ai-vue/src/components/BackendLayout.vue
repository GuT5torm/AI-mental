<template>
  <div class="backend-layout">
    <el-container class="main-container">
         <Sidebar />
      <el-container>
        <el-header>
            <Navbar />
        </el-header>
        <el-main class="main-content">
            <router-view class="content-container"></router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import Navbar from './Navbar.vue';
import Sidebar from './Sidebar.vue'
</script>

<style lang="scss" scoped>
.backend-layout {
  height: 100vh;
  .el-header {
    height: 74px !important;
    background-color: #fff;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    border-bottom: 1px solid #e5e7eb;
  }
  .main-container {
    height: 100%;
    .content-container {
      height: 100%;
      overflow-y: auto;
      padding: 20px;
      background-color: #fff;
      min-height: calc(100% - 74px);
    }
  }
}
</style>