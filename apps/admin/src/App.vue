<script setup lang="ts">
import { formatDateTime } from "./utils/format";
</script>

<template>
  <main style="padding: 24px">
    <h1>Mintpop 管控后台</h1>
    <p>工程骨架已就绪，构建于 {{ formatDateTime(new Date().toISOString()) }}。</p>
  </main>
</template>
