<script setup lang="ts">
import TheHeader from "./sections/TheHeader.vue";
import HeroSection from "./sections/HeroSection.vue";
import FeaturesSection from "./sections/FeaturesSection.vue";
import StepsSection from "./sections/StepsSection.vue";
import DownloadSection from "./sections/DownloadSection.vue";
import TheFooter from "./sections/TheFooter.vue";
</script>

<template>
  <TheHeader />
  <main>
    <HeroSection />
    <FeaturesSection />
    <StepsSection />
    <DownloadSection />
  </main>
  <TheFooter />
</template>
