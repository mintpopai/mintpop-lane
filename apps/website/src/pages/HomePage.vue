<script setup lang="ts">
import HeroSection from "../sections/HeroSection.vue";
import PricingSection from "../sections/PricingSection.vue";
import LaneSection from "../sections/LaneSection.vue";
import VerifySection from "../sections/VerifySection.vue";
import TerminalSection from "../sections/TerminalSection.vue";
import StepsSection from "../sections/StepsSection.vue";
import DownloadSection from "../sections/DownloadSection.vue";
import FaqSection from "../sections/FaqSection.vue";
</script>

<template>
  <main>
    <HeroSection />
    <!-- 叙事顺序：多少钱（席位与价格）→ 省了什么事（专属链路）→ 出问题时怎么办 → 怎么用 → 三步 → 下载 → FAQ -->
    <PricingSection />
    <LaneSection />
    <VerifySection />
    <TerminalSection />
    <StepsSection />
    <DownloadSection />
    <FaqSection />
  </main>
</template>
