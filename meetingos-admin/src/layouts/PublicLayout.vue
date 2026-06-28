<template>
  <div class="public-shell">
    <header class="public-header">
      <div class="public-brand">
        <span>会</span>
        <strong>会议管理系统</strong>
      </div>
    </header>
    <router-view />
  </div>
</template>

<style scoped>
.public-shell {
  min-height: 100vh;
  background:
    linear-gradient(180deg, rgba(219, 234, 254, 0.78), rgba(247, 251, 255, 0) 260px),
    #f7fbff;
}

.public-header {
  height: 60px;
  padding: 0 18px;
  display: flex;
  align-items: center;
}

:global(body.public-result-mode .public-header) {
  display: none;
}

.public-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.public-brand span {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #2563eb;
  background: #eaf4ff;
  border: 1px solid #bfdbfe;
  font-weight: 800;
}
</style>
