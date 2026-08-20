<script setup lang="ts">
import { REPO_URL } from "../composables/useRelease";
</script>

<template>
  <footer class="footer">
    <div class="container bar">
      <span>© {{ new Date().getFullYear() }} MintPop</span>
      <a :href="REPO_URL" target="_blank" rel="noopener">GitHub</a>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  border-top: 1px solid var(--border);
  font-size: 14px;
  color: var(--ink-3);
}

.bar {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer a {
  color: var(--ink-3);
  text-decoration: none;
}

.footer a:hover {
  color: var(--ink);
}
</style>
