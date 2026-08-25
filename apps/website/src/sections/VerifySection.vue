<script setup lang="ts">
import SituationCard from "../components/SituationCard.vue";
import { verify } from "../content/copy";
</script>

<template>
  <section id="verify" class="section section-line">
    <div class="container">
      <p class="kicker">{{ verify.kicker }}</p>
      <h2 class="section-title">{{ verify.title }}</h2>
      <p class="section-lede">{{ verify.lede }}</p>

      <div class="grid">
        <SituationCard v-for="s in verify.situations" :key="s.label" v-bind="s" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.grid {
  margin-top: 52px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
