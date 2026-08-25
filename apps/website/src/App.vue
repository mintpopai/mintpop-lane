<script setup lang="ts">
import TheHeader from "./sections/TheHeader.vue";
import HeroSection from "./sections/HeroSection.vue";
import LaneSection from "./sections/LaneSection.vue";
import VerifySection from "./sections/VerifySection.vue";
import TerminalSection from "./sections/TerminalSection.vue";
import SecuritySection from "./sections/SecuritySection.vue";
import StepsSection from "./sections/StepsSection.vue";
import DownloadSection from "./sections/DownloadSection.vue";
import FaqSection from "./sections/FaqSection.vue";
import TheFooter from "./sections/TheFooter.vue";
</script>

<template>
  <TheHeader />
  <main>
    <HeroSection />
    <!-- 叙事顺序：一人一路（是什么）→ 出口校验（凭什么可信）→ 内置终端（怎么用）
         → 安全边界（凭据怎么护）→ 三步 → 下载 → FAQ -->
    <LaneSection />
    <VerifySection />
    <TerminalSection />
    <SecuritySection />
    <StepsSection />
    <DownloadSection />
    <FaqSection />
  </main>
  <TheFooter />
</template>
