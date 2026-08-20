<script setup lang="ts">
import { REPO_URL } from "../composables/useRelease";
</script>

<template>
  <header class="header">
    <div class="container bar">
      <a class="wordmark" href="/">
        <img src="/favicon.svg" alt="" width="26" height="26" />
        MintPop Lane
      </a>
      <nav class="nav">
        <a href="#features">特性</a>
        <a href="#download">下载</a>
        <a :href="REPO_URL" target="_blank" rel="noopener">GitHub</a>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border);
}

.bar {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wordmark {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 19px;
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 28px;
  font-size: 15px;
}

.nav a {
  text-decoration: none;
  color: var(--ink-2);
  transition: color 0.15s ease;
}

.nav a:hover {
  color: var(--ink);
}
</style>
