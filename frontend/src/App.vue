<script setup lang="ts">
// 根组件仅承载路由出口
</script>

<template>
  <router-view />
</template>

<style>
:root {
  --itam-primary: #0052d9;
}
* {
  box-sizing: border-box;
}
html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
}
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Microsoft YaHei', sans-serif;
  background: #f5f7fa;
  color: #1f2329;
}
</style>
