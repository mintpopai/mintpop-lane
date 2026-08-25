<script setup lang="ts">
import TheHeader from "./sections/TheHeader.vue";
import TheFooter from "./sections/TheFooter.vue";
</script>

<template>
  <TheHeader />
  <RouterView />
  <TheFooter />
</template>
