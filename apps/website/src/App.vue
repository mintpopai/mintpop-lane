<script setup lang="ts">
import TheHeader from "./sections/TheHeader.vue";
import HeroSection from "./sections/HeroSection.vue";
import LaneSection from "./sections/LaneSection.vue";
import VerifySection from "./sections/VerifySection.vue";
import TerminalSection from "./sections/TerminalSection.vue";
import StepsSection from "./sections/StepsSection.vue";
import DownloadSection from "./sections/DownloadSection.vue";
import FaqSection from "./sections/FaqSection.vue";
import TheFooter from "./sections/TheFooter.vue";
</script>

<template>
  <TheHeader />
  <main>
    <HeroSection />
    <!-- 叙事顺序：省了什么事（专属链路）→ 出问题时怎么办 → 怎么用 → 三步 → 下载 → FAQ -->
    <LaneSection />
    <VerifySection />
    <TerminalSection />
    <StepsSection />
    <DownloadSection />
    <FaqSection />
  </main>
  <TheFooter />
</template>
