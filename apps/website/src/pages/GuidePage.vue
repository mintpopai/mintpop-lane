<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { findGuide } from "../content/guides";
import { useI18n } from "../i18n";

const route = useRoute();
const { locale } = useI18n();
const guide = computed(() => findGuide(String(route.params.slug)));
const copy = computed(() => guide.value?.copy[locale.value]);
</script>

<template>
  <main v-if="copy">
    <h1>{{ copy.h1 }}</h1>
  </main>
</template>
