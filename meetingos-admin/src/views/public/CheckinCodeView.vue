<template>
  <main class="public-page">
    <section class="public-card">
      <h1>个人签到码</h1>
      <p>请向现场工作人员出示二维码或紧凑签到码。</p>
      <canvas ref="qrCanvas" width="220" height="220"></canvas>
      <n-alert type="success" :bordered="false">{{ compactCode }}</n-alert>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { http, unwrapApi } from "@/api/http";

const route = useRoute();
const qrCanvas = ref<HTMLCanvasElement>();
const meetingId = computed(() => String(route.query.meetingId || ""));
const attendeeCode = computed(() => String(route.query.attendeeCode || route.query.attendeeId || route.query.code || ""));
const compactCode = ref("");
const fullCode = ref("");

onMounted(async () => {
  await loadCode();
});

async function loadCode() {
  try {
    if (!meetingId.value || !attendeeCode.value) throw new Error("缺少签到码参数");
    const data = unwrapApi(
      await http.get<{ compactCode: string; fullCode: string }>("/public/checkin-code", {
        params: { meetingId: meetingId.value, attendeeCode: attendeeCode.value }
      })
    );
    compactCode.value = data.compactCode;
    fullCode.value = `${window.location.origin}${data.fullCode}`;
  } catch {
    compactCode.value = `MEETINGOS-CHECKIN:${meetingId.value || "m-001"}:${attendeeCode.value || "a-004"}`;
    fullCode.value = `${window.location.origin}/public/checkin-code?type=checkin&meetingId=${meetingId.value || "m-001"}&attendeeCode=${attendeeCode.value || "a-004"}#${compactCode.value}`;
  }
  if (qrCanvas.value) {
    const { default: QRCode } = await import("qrcode");
    await QRCode.toCanvas(qrCanvas.value, fullCode.value, { width: 220, margin: 1 });
  }
}
</script>

<style scoped src="./public.css"></style>
