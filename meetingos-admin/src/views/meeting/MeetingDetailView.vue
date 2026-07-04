<template>
  <div v-if="meeting">
    <page-header :title="meeting.name">
      <status-tag :value="meeting.status" />
      <n-button @click="goBusinessPage('meeting-registration')">报名配置</n-button>
    </page-header>

    <n-card class="surface detail-card" :bordered="false">
      <template #header>
        <div class="query-card-header">
          <div>
            <h2>公开查询</h2>
            <span>配置公开查询入口，并查看参会人查询记录</span>
          </div>
          <n-button
            v-if="currentSection !== 'history'"
            type="primary"
            :loading="savingQuerySettings"
            @click="saveQuerySettings"
          >
            保存设置
          </n-button>
        </div>
      </template>

      <div class="query-settings-workspace">
                <template v-if="currentSection === 'entry'">
                  <div class="settings-pane settings-pane-grid entry-settings-grid">
                    <section class="settings-panel entry-panel">
                      <div class="entry-panel-heading">
                        <div>
                          <h3>发布入口</h3>
                          <span>选择一个入口后复制链接或扫码访问</span>
                        </div>
                        <n-radio-group v-model:value="activeLinkKey" size="small" class="entry-switch">
                          <n-radio-button v-for="item in publicQueryLinks" :key="item.key" :value="item.key">
                            {{ item.shortLabel }}
                          </n-radio-button>
                        </n-radio-group>
                      </div>

                      <div v-if="activePublicQueryLink" class="entry-preview">
                        <div class="entry-copy">
                          <strong>{{ activePublicQueryLink.label }}</strong>
                          <p v-if="activePublicQueryLink.description">{{ activePublicQueryLink.description }}</p>
                          <n-input :value="activePublicQueryLink.url" readonly size="small" />
                          <n-button-group size="small">
                            <n-button secondary @click="copyPublicQueryUrl(activePublicQueryLink.url, activePublicQueryLink.label)">
                              <template #icon><n-icon :component="CopyOutline" /></template>
                              复制
                            </n-button>
                            <n-button secondary @click="openPublicQueryUrl(activePublicQueryLink.url)">
                              <template #icon><n-icon :component="OpenOutline" /></template>
                              打开
                            </n-button>
                          </n-button-group>
                        </div>
                        <div class="qr-box">
                          <img
                            v-if="activePublicQueryLink.qrCode"
                            :src="activePublicQueryLink.qrCode"
                            :alt="`${activePublicQueryLink.label}二维码`"
                          />
                          <span v-else>生成中</span>
                        </div>
                      </div>
                    </section>

                    <section class="settings-panel entry-status-panel">
                      <div class="section-heading">
                        <strong>入口状态</strong>
                        <span>发布前先确认开关和暂停状态</span>
                      </div>
                      <div class="status-list">
                        <div class="status-row">
                          <span>座位查询</span>
                          <n-tag size="small" :type="queryForm.seatQueryEnabled ? 'success' : 'default'">
                            {{ queryForm.seatQueryEnabled ? "已开放" : "未开放" }}
                          </n-tag>
                        </div>
                        <div class="status-row">
                          <span>住宿查询</span>
                          <n-tag size="small" :type="queryForm.roomQueryEnabled ? 'success' : 'default'">
                            {{ queryForm.roomQueryEnabled ? "已开放" : "未开放" }}
                          </n-tag>
                        </div>
                        <div class="status-row">
                          <span>晚宴查询</span>
                          <n-tag size="small" :type="queryForm.dinnerQueryEnabled ? 'success' : 'default'">
                            {{ queryForm.dinnerQueryEnabled ? "已开放" : "未开放" }}
                          </n-tag>
                        </div>
                        <div class="status-row">
                          <span>查询后签到</span>
                          <n-tag size="small" :type="resultCheckinStatus.type">
                            {{ resultCheckinStatus.label }}
                          </n-tag>
                        </div>
                        <div class="status-row">
                          <span>签到独立页</span>
                          <n-tag size="small" :type="queryForm.selfCheckinEnabled ? 'success' : 'default'">
                            {{ queryForm.selfCheckinEnabled ? "已开放" : "未开放" }}
                          </n-tag>
                        </div>
                        <div class="status-row">
                          <span>公开查询</span>
                          <n-tag size="small" :type="queryForm.queryPaused ? 'warning' : 'success'">
                            {{ queryForm.queryPaused ? "已暂停" : "正常" }}
                          </n-tag>
                        </div>
                      </div>
                    </section>

                    <section class="settings-panel entry-switch-panel">
                      <div class="section-heading">
                        <strong>入口开关</strong>
                        <span>分别控制公开入口和查询后签到能力是否可用</span>
                      </div>
                      <div class="toggle-list entry-toggle-grid">
                        <div class="toggle-row">
                          <div>
                            <strong>座位查询</strong>
                            <span>查询座位号</span>
                          </div>
                          <n-switch v-model:value="queryForm.seatQueryEnabled" />
                        </div>
                        <div class="toggle-row">
                          <div>
                            <strong>住宿查询</strong>
                            <span>查询房间号</span>
                          </div>
                          <n-switch v-model:value="queryForm.roomQueryEnabled" />
                        </div>
                        <div class="toggle-row">
                          <div>
                            <strong>晚宴查询</strong>
                            <span>查询晚宴桌号</span>
                          </div>
                          <n-switch v-model:value="queryForm.dinnerQueryEnabled" />
                        </div>
                        <div class="toggle-row">
                          <div>
                            <strong>签到独立页</strong>
                            <span>允许访问 /public/checkin 自助签到页，关闭后隐藏入口并拦截独立页提交</span>
                          </div>
                          <n-switch v-model:value="queryForm.selfCheckinEnabled" />
                        </div>
                      </div>
                    </section>

                    <section class="settings-panel access-control-panel">
                      <div class="section-heading">
                        <strong>访问控制</strong>
                        <span>临时暂停或设置公开查询的有效期</span>
                      </div>
                      <n-form label-placement="top" :show-feedback="false" class="query-form">
                        <div class="query-form-row">
                          <n-form-item label="开始时间">
                            <n-date-picker
                              v-model:formatted-value="queryForm.startTime"
                              clearable
                              type="datetime"
                              value-format="yyyy-MM-dd'T'HH:mm:ss"
                              placeholder="不限开始时间"
                            />
                          </n-form-item>
                          <n-form-item label="结束时间">
                            <n-date-picker
                              v-model:formatted-value="queryForm.endTime"
                              clearable
                              type="datetime"
                              value-format="yyyy-MM-dd'T'HH:mm:ss"
                              placeholder="不限结束时间"
                            />
                          </n-form-item>
                        </div>
                        <div class="toggle-list compact-toggle-list">
                          <div class="toggle-row">
                            <div>
                              <strong>暂停公开查询</strong>
                              <span>开启后公开页仍可访问，但提交查询会被拦截</span>
                            </div>
                            <n-switch v-model:value="queryForm.queryPaused" />
                          </div>
                        </div>
                      </n-form>
                    </section>

                    <section class="settings-panel result-appearance-panel">
                      <div class="section-heading">
                        <strong>外观</strong>
                        <span>配置公开查询页的基础视觉</span>
                      </div>
                      <n-form label-placement="top" :show-feedback="false" class="query-form result-appearance-form">
                        <div class="query-form-row">
                          <n-form-item label="主题色">
                            <div class="theme-color-field">
                              <n-color-picker v-model:value="queryForm.themeColor" :show-alpha="false" />
                              <n-input v-model:value="queryForm.themeColor" placeholder="#2563eb" />
                            </div>
                          </n-form-item>
                        </div>
                        <div class="query-form-row asset-fields-row">
                          <n-form-item label="Logo URL">
                            <div class="asset-url-field">
                              <n-input v-model:value="queryForm.logoUrl" clearable placeholder="https://..." />
                              <n-upload
                                :show-file-list="false"
                                accept=".png,.jpg,.jpeg,.webp,.gif"
                                :custom-request="uploadLogoAsset"
                              >
                                <n-button secondary :loading="uploadingAsset === 'logo'">
                                  <template #icon><n-icon :component="CloudUploadOutline" /></template>
                                  上传
                                </n-button>
                              </n-upload>
                            </div>
                          </n-form-item>
                          <n-form-item label="背景图 URL">
                            <div class="asset-url-field">
                              <n-input v-model:value="queryForm.backgroundUrl" clearable placeholder="https://..." />
                              <n-upload
                                :show-file-list="false"
                                accept=".png,.jpg,.jpeg,.webp,.gif"
                                :custom-request="uploadBackgroundAsset"
                              >
                                <n-button secondary :loading="uploadingAsset === 'background'">
                                  <template #icon><n-icon :component="CloudUploadOutline" /></template>
                                  上传
                                </n-button>
                              </n-upload>
                            </div>
                          </n-form-item>
                        </div>
                      </n-form>
                    </section>
                  </div>
                </template>

              <template v-else-if="currentSection === 'content'">
                <div class="settings-pane content-settings-pane">
                  <section class="settings-panel query-rules-panel">
                    <div class="section-heading">
                      <strong>查询功能</strong>
                      <span>控制公开页需要填写哪些信息，以及多人命中时是否允许展示多条</span>
                    </div>
                    <n-form label-placement="top" :show-feedback="false" class="query-form query-rules-form">
                      <n-form-item label="查询字段">
                        <n-select
                          v-model:value="queryForm.fields"
                          multiple
                          :options="fieldSelectOptions"
                          placeholder="选择参会人需要填写的字段"
                        />
                      </n-form-item>
                      <n-form-item label="匹配方式">
                        <n-radio-group v-model:value="queryForm.matchMode">
                          <n-radio-button value="FUZZY">模糊匹配</n-radio-button>
                          <n-radio-button value="EXACT">精确匹配</n-radio-button>
                        </n-radio-group>
                      </n-form-item>
                      <div class="toggle-row compact-toggle-row">
                        <div>
                          <strong>允许显示多条结果</strong>
                          <span>同一查询条件命中多位参会人时，公开页最多展示 20 条审核通过的结果</span>
                        </div>
                        <n-switch v-model:value="queryForm.allowMultipleResults" />
                      </div>
                    </n-form>
                  </section>

                  <section class="settings-panel page-content-panel">
                    <div class="section-heading">
                      <strong>页面信息</strong>
                      <span>每个公开入口单独配置标题、说明、公告和提示文案</span>
                    </div>
                    <n-tabs v-model:value="activeContentPageKey" type="segment" animated class="content-page-tabs">
                      <n-tab-pane v-for="page in publicContentPages" :key="page.key" :name="page.key" :tab="page.label" />
                    </n-tabs>
                    <n-form label-placement="top" :show-feedback="false" class="query-form page-content-form">
                      <n-form-item :label="`${activeContentPage.label}标题`" class="content-title-field">
                        <n-input v-model:value="activePageContent.title" clearable :placeholder="activeContentPage.titlePlaceholder" />
                      </n-form-item>
                      <n-form-item label="页面说明" class="content-description-field">
                        <n-input
                          v-model:value="activePageContent.description"
                          type="textarea"
                          clearable
                          :autosize="{ minRows: 2, maxRows: 4 }"
                          placeholder="用于提示参会人填写规则、查询范围等；清空后该页不显示说明"
                        />
                      </n-form-item>
                      <n-form-item label="公告" class="content-announcement-field">
                        <n-input
                          v-model:value="activePageContent.announcement"
                          type="textarea"
                          clearable
                          :autosize="{ minRows: 2, maxRows: 5 }"
                          placeholder="例如：座位和房间信息如有调整，请以现场安排为准；清空后该页不显示公告"
                        />
                      </n-form-item>
                      <div class="query-form-row content-two-column-row">
                        <n-form-item label="发布人">
                          <n-input v-model:value="activePageContent.publisher" clearable placeholder="例如：会务组" />
                        </n-form-item>
                        <n-form-item v-if="activeContentPage.hasButton" label="按钮文案">
                          <n-input v-model:value="activePageContent.buttonText" clearable :placeholder="activeContentPage.buttonPlaceholder" />
                        </n-form-item>
                      </div>
                      <n-form-item v-if="activeContentPage.hasEmptyMessage" label="空结果提示" class="content-full-field">
                        <n-input v-model:value="activePageContent.emptyMessage" clearable placeholder="未查询到结果，请核对信息后重试" />
                      </n-form-item>
                      <n-form-item v-if="activeContentPage.hasResultCheckin" label="查询后签到按钮" class="content-full-field">
                        <div class="toggle-row compact-toggle-row page-checkin-toggle">
                          <div>
                            <strong>显示“立即签到”</strong>
                            <span>当前页查询成功后显示签到按钮，关闭后该页不展示并由后端拦截提交</span>
                          </div>
                          <n-switch v-model:value="activePageContent.checkinEnabled" />
                        </div>
                      </n-form-item>
                      <n-form-item v-if="activeContentPageKey === 'combined'" label="综合查询展示内容" class="content-full-field">
                        <div class="combined-type-switches">
                          <div class="toggle-row compact-toggle-row">
                            <div>
                              <strong>座位结果</strong>
                              <span>综合查询后展示座位信息</span>
                            </div>
                            <n-switch
                              :value="combinedQueryTypeEnabled('SEAT')"
                              :disabled="!queryForm.seatQueryEnabled"
                              @update:value="setCombinedSeatQueryType"
                            />
                          </div>
                          <div class="toggle-row compact-toggle-row">
                            <div>
                              <strong>住宿结果</strong>
                              <span>综合查询后展示住宿信息</span>
                            </div>
                            <n-switch
                              :value="combinedQueryTypeEnabled('ROOM')"
                              :disabled="!queryForm.roomQueryEnabled"
                              @update:value="setCombinedRoomQueryType"
                            />
                          </div>
                          <div class="toggle-row compact-toggle-row">
                            <div>
                              <strong>晚宴结果</strong>
                              <span>综合查询后展示晚宴桌号</span>
                            </div>
                            <n-switch
                              :value="combinedQueryTypeEnabled('DINNER')"
                              :disabled="!queryForm.dinnerQueryEnabled"
                              @update:value="setCombinedDinnerQueryType"
                            />
                          </div>
                        </div>
                      </n-form-item>
                      <n-form-item v-if="activeContentPage.hasNotice" label="结果提示" class="content-full-field">
                        <n-input
                          v-model:value="activePageContent.notice"
                          type="textarea"
                          clearable
                          :autosize="{ minRows: 2, maxRows: 4 }"
                          :placeholder="activeContentPage.noticePlaceholder"
                        />
                      </n-form-item>
                    </n-form>
                  </section>

                  <section class="settings-panel settings-panel-wide result-fields-panel">
                    <div class="section-heading">
                      <strong>结果字段</strong>
                      <span>座位、住宿和晚宴入口分别配置；综合查询页会按结果类型使用对应配置</span>
                    </div>
                    <div class="result-field-config-grid">
                      <div class="result-field-config">
                        <div class="section-heading compact-heading">
                          <strong>座位结果字段</strong>
                          <span>用于座位查询入口和综合页座位结果</span>
                        </div>
                        <n-form label-placement="top" :show-feedback="false" class="query-form">
                          <n-form-item label="展示字段">
                            <n-select
                              v-model:value="queryForm.seatVisibleResultFields"
                              multiple
                              :options="seatResultFieldSelectOptions"
                              placeholder="选择座位结果中展示的字段"
                            />
                          </n-form-item>
                          <n-form-item label="展示顺序">
                            <div class="field-order-list">
                              <div v-for="(field, index) in queryForm.seatVisibleResultFields" :key="field" class="field-order-row">
                                <span class="field-order-index">{{ index + 1 }}</span>
                                <strong>{{ resultFieldLabel(field) }}</strong>
                                <n-button-group size="tiny" class="field-order-actions">
                                  <n-button secondary circle :disabled="index === 0" title="上移" @click="moveSeatVisibleResultField(index, -1)">
                                    <template #icon><n-icon :component="ArrowUpOutline" /></template>
                                  </n-button>
                                  <n-button
                                    secondary
                                    circle
                                    :disabled="index === queryForm.seatVisibleResultFields.length - 1"
                                    title="下移"
                                    @click="moveSeatVisibleResultField(index, 1)"
                                  >
                                    <template #icon><n-icon :component="ArrowDownOutline" /></template>
                                  </n-button>
                                </n-button-group>
                              </div>
                            </div>
                          </n-form-item>
                          <n-form-item label="脱敏字段">
                            <n-select
                              v-model:value="queryForm.seatMaskedResultFields"
                              multiple
                              :options="visibleSeatResultFieldSelectOptions"
                              placeholder="选择座位结果需要脱敏的字段"
                            />
                          </n-form-item>
                          <n-form-item label="重点字段">
                            <n-select
                              v-model:value="queryForm.seatHighlightResultFields"
                              multiple
                              :options="visibleSeatResultFieldSelectOptions"
                              placeholder="选择座位结果需要高亮的字段"
                            />
                          </n-form-item>
                        </n-form>
                      </div>

                      <div class="result-field-config">
                        <div class="section-heading compact-heading">
                          <strong>住宿结果字段</strong>
                          <span>用于住宿查询入口和综合页住宿结果</span>
                        </div>
                        <n-form label-placement="top" :show-feedback="false" class="query-form">
                          <n-form-item label="展示字段">
                            <n-select
                              v-model:value="queryForm.roomVisibleResultFields"
                              multiple
                              :options="roomResultFieldSelectOptions"
                              placeholder="选择住宿结果中展示的字段"
                            />
                          </n-form-item>
                          <n-form-item label="展示顺序">
                            <div class="field-order-list">
                              <div v-for="(field, index) in queryForm.roomVisibleResultFields" :key="field" class="field-order-row">
                                <span class="field-order-index">{{ index + 1 }}</span>
                                <strong>{{ resultFieldLabel(field) }}</strong>
                                <n-button-group size="tiny" class="field-order-actions">
                                  <n-button secondary circle :disabled="index === 0" title="上移" @click="moveRoomVisibleResultField(index, -1)">
                                    <template #icon><n-icon :component="ArrowUpOutline" /></template>
                                  </n-button>
                                  <n-button
                                    secondary
                                    circle
                                    :disabled="index === queryForm.roomVisibleResultFields.length - 1"
                                    title="下移"
                                    @click="moveRoomVisibleResultField(index, 1)"
                                  >
                                    <template #icon><n-icon :component="ArrowDownOutline" /></template>
                                  </n-button>
                                </n-button-group>
                              </div>
                            </div>
                          </n-form-item>
                          <n-form-item label="脱敏字段">
                            <n-select
                              v-model:value="queryForm.roomMaskedResultFields"
                              multiple
                              :options="visibleRoomResultFieldSelectOptions"
                              placeholder="选择住宿结果需要脱敏的字段"
                            />
                          </n-form-item>
                          <n-form-item label="重点字段">
                            <n-select
                              v-model:value="queryForm.roomHighlightResultFields"
                              multiple
                              :options="visibleRoomResultFieldSelectOptions"
                              placeholder="选择住宿结果需要高亮的字段"
                            />
                          </n-form-item>
                        </n-form>
                      </div>

                      <div class="result-field-config">
                        <div class="section-heading compact-heading">
                          <strong>晚宴结果字段</strong>
                          <span>用于晚宴查询入口和综合页晚宴结果</span>
                        </div>
                        <n-form label-placement="top" :show-feedback="false" class="query-form">
                          <n-form-item label="展示字段">
                            <n-select
                              v-model:value="queryForm.dinnerVisibleResultFields"
                              multiple
                              :options="dinnerResultFieldSelectOptions"
                              placeholder="选择晚宴结果中展示的字段"
                            />
                          </n-form-item>
                          <n-form-item label="展示顺序">
                            <div class="field-order-list">
                              <div v-for="(field, index) in queryForm.dinnerVisibleResultFields" :key="field" class="field-order-row">
                                <span class="field-order-index">{{ index + 1 }}</span>
                                <strong>{{ resultFieldLabel(field) }}</strong>
                                <n-button-group size="tiny" class="field-order-actions">
                                  <n-button secondary circle :disabled="index === 0" title="上移" @click="moveDinnerVisibleResultField(index, -1)">
                                    <template #icon><n-icon :component="ArrowUpOutline" /></template>
                                  </n-button>
                                  <n-button
                                    secondary
                                    circle
                                    :disabled="index === queryForm.dinnerVisibleResultFields.length - 1"
                                    title="下移"
                                    @click="moveDinnerVisibleResultField(index, 1)"
                                  >
                                    <template #icon><n-icon :component="ArrowDownOutline" /></template>
                                  </n-button>
                                </n-button-group>
                              </div>
                            </div>
                          </n-form-item>
                          <n-form-item label="脱敏字段">
                            <n-select
                              v-model:value="queryForm.dinnerMaskedResultFields"
                              multiple
                              :options="visibleDinnerResultFieldSelectOptions"
                              placeholder="选择晚宴结果需要脱敏的字段"
                            />
                          </n-form-item>
                          <n-form-item label="重点字段">
                            <n-select
                              v-model:value="queryForm.dinnerHighlightResultFields"
                              multiple
                              :options="visibleDinnerResultFieldSelectOptions"
                              placeholder="选择晚宴结果需要高亮的字段"
                            />
                          </n-form-item>
                        </n-form>
                      </div>
                    </div>
                  </section>
                </div>
              </template>

              <template v-else-if="currentSection === 'test'">
                <div class="settings-pane">
                  <section class="settings-panel">
                    <div class="section-heading">
                      <strong>后台查询测试</strong>
                      <span>按当前字段配置排查座位或住宿查询命中情况，最多返回 20 条候选记录</span>
                    </div>
                    <div class="admin-query-content">
                      <div class="admin-query-toolbar">
                        <div class="query-test-fields">
                          <n-input
                            v-for="field in queryForm.fields"
                            :key="field"
                            v-model:value="queryValues[field]"
                            clearable
                            :placeholder="fieldLabel(field)"
                          />
                        </div>
                        <n-button-group>
                          <n-button :loading="testingQuery && testQueryType === 'seat'" @click="runAdminQuery('seat')">查座位</n-button>
                          <n-button :loading="testingQuery && testQueryType === 'room'" @click="runAdminQuery('room')">查住宿</n-button>
                        </n-button-group>
                      </div>
                      <n-data-table
                        v-if="adminQueryTouched || queryResults.length"
                        :columns="queryResultColumns"
                        :data="queryResults"
                        :pagination="{ pageSize: 5 }"
                      />
                      <div v-else class="admin-query-placeholder">
                        填写字段后执行后台查询，最多返回 20 条候选记录。
                      </div>
                    </div>
                  </section>
                </div>
              </template>

          <div v-else class="query-history-panel">
            <div class="query-history-toolbar">
              <n-radio-group v-model:value="historyView" size="small">
                <n-radio-button value="logs">查询日志</n-radio-button>
                <n-radio-button value="summary">汇总统计</n-radio-button>
              </n-radio-group>

              <n-button
                v-if="historyView === 'summary'"
                size="small"
                :loading="loadingQuerySummary"
                @click="loadQuerySummary"
              >
                刷新汇总
              </n-button>
            </div>

            <template v-if="historyView === 'logs'">
              <div class="history-filters">
                <n-select
                  v-model:value="historyFilters.type"
                  clearable
                  size="small"
                  :options="queryTypeFilterOptions"
                  placeholder="全部类型"
                />
                <n-select
                  v-model:value="historyFilters.status"
                  clearable
                  size="small"
                  :options="queryStatusFilterOptions"
                  placeholder="全部状态"
                />
                <n-input
                  v-model:value="historyFilters.keyword"
                  clearable
                  size="small"
                  placeholder="搜索输入、参会人、结果或失败原因"
                  @keyup.enter="loadQueryHistory(1)"
                />
                <n-button size="small" :loading="loadingQueryHistory" @click="loadQueryHistory(1)">查询</n-button>
              </div>
              <n-data-table
                :columns="queryHistoryColumns"
                :data="queryHistoryRows"
                :loading="loadingQueryHistory"
                :pagination="false"
                :bordered="false"
                :scroll-x="1120"
              />
              <div class="history-pagination">
                <n-pagination
                  v-model:page="historyPagination.page"
                  v-model:page-size="historyPagination.pageSize"
                  show-size-picker
                  :page-sizes="[10, 20, 50]"
                  :item-count="historyPagination.itemCount"
                  @update:page="handleHistoryPageChange"
                  @update:page-size="handleHistoryPageSizeChange"
                />
              </div>
            </template>

            <n-data-table
              v-else
              :columns="querySummaryColumns"
              :data="querySummaryRows"
              :loading="loadingQuerySummary"
              :pagination="{ pageSize: 20 }"
              :bordered="false"
              :scroll-x="900"
            />
          </div>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, h, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  NIcon,
  NTag,
  useMessage,
  type DataTableColumns,
  type UploadCustomRequestOptions
} from "naive-ui";
import { ArrowDownOutline, ArrowUpOutline, CloudUploadOutline, CopyOutline, OpenOutline } from "@vicons/ionicons5";
import PageHeader from "@/components/PageHeader.vue";
import StatusTag from "@/components/StatusTag.vue";
import { useMeetingStore } from "@/stores/meeting";
import { formatDateTimeLabel } from "@/utils/meetingDisplay";
import { assertSafeImageUrl, isAllowedUploadImage } from "@/utils/safeUrl";
import type {
  AdminQueryCandidate,
  CombinedQueryType,
  PublicPageContent,
  PublicPageKey,
  PublicQueryField,
  PublicQueryHistory,
  PublicQueryMatchMode,
  PublicQueryResultField,
  PublicQueryStatus,
  PublicQuerySummary,
  PublicQueryType
} from "@/utils/types";

const route = useRoute();
const router = useRouter();
const store = useMeetingStore();
const message = useMessage();
/*
 * 查询设置页首屏只需要发布入口和状态摘要；历史表格、上传、日期与颜色选择器都在后续 Tab。
 * 使用异步组件可以避免进入 /detail 时一次性解析完整 Naive UI 重控件，
 * 尤其是在用户刚从住宿/统计页快速切过来时，能明显降低浏览器主线程峰值。
 */
// 注意不要动态 import("naive-ui") 根入口，否则会把整套 Naive UI 打成一个近 MB 级异步块。
const NColorPicker = defineAsyncComponent(() => import("naive-ui/es/color-picker").then((module) => module.NColorPicker));
const NDataTable = defineAsyncComponent(() => import("naive-ui/es/data-table").then((module) => module.NDataTable));
const NDatePicker = defineAsyncComponent(() => import("naive-ui/es/date-picker").then((module) => module.NDatePicker));
const NUpload = defineAsyncComponent(() => import("naive-ui/es/upload").then((module) => module.NUpload));
const meetingId = computed(() => String(route.params.meetingId || route.params.id || ""));
const meeting = computed(() => store.meetings.find((item) => item.id === meetingId.value));
const savingQuerySettings = ref(false);
const testingQuery = ref(false);
const testQueryType = ref<"seat" | "room">("seat");
const queryResults = ref<AdminQueryCandidate[]>([]);
const adminQueryTouched = ref(false);
type QuerySectionKey = "entry" | "content" | "test" | "history";
const currentSection = computed<QuerySectionKey>(() => {
  const rawValue = Array.isArray(route.query.section) ? route.query.section[0] : route.query.section;
  return rawValue === "content" || rawValue === "test" || rawValue === "history" ? rawValue : "entry";
});
const historyView = ref<"logs" | "summary">("logs");
const loadingQueryHistory = ref(false);
const loadingQuerySummary = ref(false);
const uploadingAsset = ref<"logo" | "background" | null>(null);
const queryHistoryRows = ref<PublicQueryHistory[]>([]);
const querySummaryRows = ref<PublicQuerySummary[]>([]);
type PublicQueryLinkKey = "combined" | "seat" | "room" | "dinner" | "checkin";

interface PublicQueryLink {
  key: PublicQueryLinkKey;
  label: string;
  shortLabel: string;
  description: string;
  url: string;
  qrCode: string;
}

interface PublicContentPage {
  key: PublicPageKey;
  label: string;
  titlePlaceholder: string;
  buttonPlaceholder: string;
  noticePlaceholder: string;
  hasButton: boolean;
  hasEmptyMessage: boolean;
  hasNotice: boolean;
  hasResultCheckin: boolean;
}

const defaultPageContents: Record<PublicPageKey, PublicPageContent> = {
  entry: { title: "", description: "", announcement: "", publisher: "" },
  combined: { title: "", description: "", announcement: "", publisher: "", buttonText: "立即查询", emptyMessage: "未查询到结果，请核对信息后重试", checkinEnabled: true },
  seat: {
    title: "",
    description: "",
    announcement: "",
    publisher: "",
    buttonText: "立即查询",
    emptyMessage: "未查询到结果，请核对信息后重试",
    notice: "请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。",
    checkinEnabled: true
  },
  room: {
    title: "",
    description: "",
    announcement: "",
    publisher: "",
    buttonText: "立即查询",
    emptyMessage: "未查询到结果，请核对信息后重试",
    notice: "凭会议证件及身份证办理入住，接待台位于酒店大堂。",
    checkinEnabled: true
  },
  dinner: {
    title: "",
    description: "",
    announcement: "",
    publisher: "",
    buttonText: "立即查询",
    emptyMessage: "未查询到结果，请核对信息后重试",
    notice: "请按现场指引前往晚宴区域入座，如有调整请以会务组通知为准。",
    checkinEnabled: true
  },
  checkin: { title: "", description: "", announcement: "", publisher: "", buttonText: "自助签到" }
};

const publicContentPages: PublicContentPage[] = [
  {
    key: "entry",
    label: "入口页",
    titlePlaceholder: "例如：参会人入口",
    buttonPlaceholder: "",
    noticePlaceholder: "",
    hasButton: false,
    hasEmptyMessage: false,
    hasNotice: false,
    hasResultCheckin: false
  },
  {
    key: "combined",
    label: "综合查询",
    titlePlaceholder: "例如：会议综合查询",
    buttonPlaceholder: "立即查询",
    noticePlaceholder: "",
    hasButton: true,
    hasEmptyMessage: true,
    hasNotice: false,
    hasResultCheckin: true
  },
  {
    key: "seat",
    label: "座位查询",
    titlePlaceholder: "例如：座位查询",
    buttonPlaceholder: "立即查询",
    noticePlaceholder: "例如：请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。",
    hasButton: true,
    hasEmptyMessage: true,
    hasNotice: true,
    hasResultCheckin: true
  },
  {
    key: "room",
    label: "住宿查询",
    titlePlaceholder: "例如：房间号查询",
    buttonPlaceholder: "立即查询",
    noticePlaceholder: "例如：凭身份证办理入住，接待台位于酒店大堂。",
    hasButton: true,
    hasEmptyMessage: true,
    hasNotice: true,
    hasResultCheckin: true
  },
  {
    key: "dinner",
    label: "晚宴查询",
    titlePlaceholder: "例如：晚宴桌号查询",
    buttonPlaceholder: "立即查询",
    noticePlaceholder: "例如：请按现场指引前往晚宴区域入座，如有调整请以会务组通知为准。",
    hasButton: true,
    hasEmptyMessage: true,
    hasNotice: true,
    hasResultCheckin: true
  },
  {
    key: "checkin",
    label: "自助签到",
    titlePlaceholder: "例如：会议自助签到",
    buttonPlaceholder: "自助签到",
    noticePlaceholder: "",
    hasButton: true,
    hasEmptyMessage: false,
    hasNotice: false,
    hasResultCheckin: false
  }
];

const queryForm = reactive({
  seatQueryEnabled: false,
  roomQueryEnabled: false,
  dinnerQueryEnabled: false,
  checkinEnabled: true,
  selfCheckinEnabled: true,
  queryPaused: false,
  fields: ["PHONE", "NAME"] as PublicQueryField[],
  matchMode: "FUZZY" as PublicQueryMatchMode,
  allowMultipleResults: false,
  combinedQueryTypes: ["SEAT", "ROOM", "DINNER"] as Array<"SEAT" | "ROOM" | "DINNER">,
  pageTitle: "",
  pageDescription: "",
  announcement: "",
  publisher: "",
  startTime: null as string | null,
  endTime: null as string | null,
  buttonText: "立即查询",
  emptyMessage: "未查询到结果，请核对信息后重试",
  seatNotice: "请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。",
  roomNotice: "凭会议证件及身份证办理入住，接待台位于酒店大堂。",
  dinnerNotice: "请按现场指引前往晚宴区域入座，如有调整请以会务组通知为准。",
  pageContents: clonePageContents(defaultPageContents),
  themeColor: "#2563eb",
  logoUrl: "",
  backgroundUrl: "",
  visibleResultFields: [
    "ATTENDEE_NAME",
    "SEAT_AREA",
    "SEAT_ROW",
    "SEAT_NO",
    "SEAT_CODE",
    "HOTEL_NAME",
    "ROOM_TYPE",
    "ROOM_NO",
    "ROOM_REMARK",
    "CHECKIN_TIPS",
    "DINNER_TABLE_NO",
    "DINNER_VENUE",
    "DINNER_TIME",
    "DINNER_REMARK"
  ] as PublicQueryResultField[],
  maskedResultFields: [] as PublicQueryResultField[],
  highlightResultFields: ["SEAT_CODE", "ROOM_NO", "DINNER_TABLE_NO"] as PublicQueryResultField[],
  seatVisibleResultFields: ["ATTENDEE_NAME", "SEAT_AREA", "SEAT_ROW", "SEAT_NO", "SEAT_CODE"] as PublicQueryResultField[],
  seatMaskedResultFields: [] as PublicQueryResultField[],
  seatHighlightResultFields: ["SEAT_CODE"] as PublicQueryResultField[],
  roomVisibleResultFields: ["ATTENDEE_NAME", "HOTEL_NAME", "ROOM_TYPE", "ROOM_NO", "ROOM_REMARK", "CHECKIN_TIPS"] as PublicQueryResultField[],
  roomMaskedResultFields: [] as PublicQueryResultField[],
  roomHighlightResultFields: ["ROOM_NO"] as PublicQueryResultField[],
  dinnerVisibleResultFields: ["ATTENDEE_NAME", "DINNER_TABLE_NO", "DINNER_VENUE", "DINNER_TIME", "DINNER_REMARK"] as PublicQueryResultField[],
  dinnerMaskedResultFields: [] as PublicQueryResultField[],
  dinnerHighlightResultFields: ["DINNER_TABLE_NO"] as PublicQueryResultField[]
});
const queryValues = reactive<Record<PublicQueryField, string>>({
  NAME: "",
  PHONE: "",
  PHONE_LAST4: "",
  ATTENDEE_CODE: "",
  ORGANIZATION: "",
  TITLE: "",
  EMAIL: ""
});

const fieldOptions = ref([
  { field: "PHONE" as PublicQueryField, label: "手机号" },
  { field: "NAME" as PublicQueryField, label: "姓名" }
]);
const resultFieldOptions = ref([
  { field: "ATTENDEE_NAME" as PublicQueryResultField, label: "姓名" },
  { field: "SEAT_CODE" as PublicQueryResultField, label: "座位号" },
  { field: "ROOM_NO" as PublicQueryResultField, label: "房间号" }
]);
const activeLinkKey = ref<PublicQueryLinkKey>("combined");
const activeContentPageKey = ref<PublicPageKey>("combined");
const queryLinkQrcodes = ref<Record<string, string>>({});
let qrRenderToken = 0;
let qrIdleHandle: number | undefined;
const historyFilters = reactive<{
  type: PublicQueryType | null;
  status: PublicQueryStatus | null;
  keyword: string;
}>({
  type: null,
  status: null,
  keyword: ""
});
const historyPagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0
});
const fieldSelectOptions = computed(() => fieldOptions.value.map((item) => ({ label: item.label, value: item.field })));
const resultFieldSelectOptions = computed(() => resultFieldOptions.value.map((item) => ({ label: item.label, value: item.field })));
const activeContentPage = computed(() => publicContentPages.find((item) => item.key === activeContentPageKey.value) || publicContentPages[0]);
const activePageContent = computed(() => queryForm.pageContents[activeContentPageKey.value] || queryForm.pageContents.combined);
const resultCheckinPageKeys: Array<Extract<PublicPageKey, "combined" | "seat" | "room" | "dinner">> = ["combined", "seat", "room", "dinner"];
const resultCheckinStatus = computed(() => {
  const enabledCount = resultCheckinPageKeys.filter((key) => queryForm.pageContents[key].checkinEnabled !== false).length;
  if (enabledCount === resultCheckinPageKeys.length) return { label: "全部开放", type: "success" as const };
  if (enabledCount > 0) return { label: "部分开放", type: "warning" as const };
  return { label: "未开放", type: "default" as const };
});
const seatResultFields = new Set<PublicQueryResultField>([
  "ATTENDEE_NAME",
  "ATTENDEE_PHONE",
  "ORGANIZATION",
  "TITLE",
  "EMAIL",
  "MEETING_NAME",
  "SEAT_AREA",
  "SEAT_ROW",
  "SEAT_NO",
  "SEAT_CODE"
]);
const roomResultFields = new Set<PublicQueryResultField>([
  "ATTENDEE_NAME",
  "ATTENDEE_PHONE",
  "ORGANIZATION",
  "TITLE",
  "EMAIL",
  "MEETING_NAME",
  "HOTEL_NAME",
  "ROOM_TYPE",
  "ROOM_NO",
  "ROOM_REMARK",
  "CHECKIN_TIPS"
]);
const dinnerResultFields = new Set<PublicQueryResultField>([
  "ATTENDEE_NAME",
  "ATTENDEE_PHONE",
  "ORGANIZATION",
  "TITLE",
  "EMAIL",
  "MEETING_NAME",
  "DINNER_TABLE_NO",
  "DINNER_VENUE",
  "DINNER_TIME",
  "DINNER_REMARK"
]);
const seatResultFieldSelectOptions = computed(() =>
  resultFieldOptions.value.filter((item) => seatResultFields.has(item.field)).map((item) => ({ label: item.label, value: item.field }))
);
const roomResultFieldSelectOptions = computed(() =>
  resultFieldOptions.value.filter((item) => roomResultFields.has(item.field)).map((item) => ({ label: item.label, value: item.field }))
);
const dinnerResultFieldSelectOptions = computed(() =>
  resultFieldOptions.value.filter((item) => dinnerResultFields.has(item.field)).map((item) => ({ label: item.label, value: item.field }))
);
const visibleResultFieldSelectOptions = computed(() => {
  const visible = new Set(queryForm.visibleResultFields);
  return resultFieldOptions.value.filter((item) => visible.has(item.field)).map((item) => ({ label: item.label, value: item.field }));
});
const visibleSeatResultFieldSelectOptions = computed(() => {
  const visible = new Set(queryForm.seatVisibleResultFields);
  return resultFieldOptions.value.filter((item) => visible.has(item.field)).map((item) => ({ label: item.label, value: item.field }));
});
const visibleRoomResultFieldSelectOptions = computed(() => {
  const visible = new Set(queryForm.roomVisibleResultFields);
  return resultFieldOptions.value.filter((item) => visible.has(item.field)).map((item) => ({ label: item.label, value: item.field }));
});
const visibleDinnerResultFieldSelectOptions = computed(() => {
  const visible = new Set(queryForm.dinnerVisibleResultFields);
  return resultFieldOptions.value.filter((item) => visible.has(item.field)).map((item) => ({ label: item.label, value: item.field }));
});
const queryTypeFilterOptions = [
  { label: "座位查询", value: "SEAT" as PublicQueryType },
  { label: "住宿查询", value: "ROOM" as PublicQueryType },
  { label: "晚宴查询", value: "DINNER" as PublicQueryType },
  { label: "签到", value: "CHECKIN" as PublicQueryType }
];
const queryStatusFilterOptions = [
  { label: "成功", value: "SUCCESS" as PublicQueryStatus },
  { label: "失败", value: "FAILED" as PublicQueryStatus }
];
const publicQueryLinks = computed<PublicQueryLink[]>(() => {
  if (!meeting.value) return [];
  const token = encodeURIComponent(meeting.value.publicQueryToken || "");
  const query = `token=${token}`;
  return [
    {
      key: "combined" as const,
      label: "综合查询入口",
      shortLabel: "综合",
      description: "",
      url: `${window.location.origin}/public/query?${query}`
    },
    {
      key: "seat" as const,
      label: "座位查询入口",
      shortLabel: "座位",
      description: "仅查询座位结果，适合座位公布场景单独使用。",
      url: `${window.location.origin}/public/seat-query?${query}`
    },
    {
      key: "room" as const,
      label: "住宿查询入口",
      shortLabel: "住宿",
      description: "仅查询住宿结果，适合酒店入住通知单独使用。",
      url: `${window.location.origin}/public/room-query?${query}`
    },
    {
      key: "dinner" as const,
      label: "晚宴查询入口",
      shortLabel: "晚宴",
      description: "仅查询晚宴桌号，适合晚宴入座指引单独使用。",
      url: `${window.location.origin}/public/dinner-query?${query}`
    },
    {
      key: "checkin" as const,
      label: "签到入口",
      shortLabel: "签到",
      description: "独立自助签到入口，受签到独立页开关控制，适合现场入口扫码使用。",
      url: `${window.location.origin}/public/checkin?${query}`
    }
  ].map((item) => ({
    ...item,
    qrCode: queryLinkQrcodes.value[item.key] || ""
  }));
});
const publicQueryLinkUrls = computed(() => publicQueryLinks.value.map((item) => `${item.key}:${item.url}`).join("|"));
const activePublicQueryLinkUrl = computed(() => activePublicQueryLink.value?.url || "");
const activePublicQueryLink = computed(() => {
  return publicQueryLinks.value.find((item) => item.key === activeLinkKey.value) || publicQueryLinks.value[0];
});
const queryResultColumns: DataTableColumns<AdminQueryCandidate> = [
  { title: "姓名", key: "attendeeName" },
  { title: "手机号", key: "phone" },
  { title: "单位", key: "organization" },
  {
    title: "审核",
    key: "reviewStatus",
    render(row) {
      const approved = row.reviewStatus === "APPROVED";
      return h(NTag, { size: "small", type: approved ? "success" : "warning" }, { default: () => (approved ? "已通过" : row.reviewStatus) });
    }
  },
  { title: "座位", key: "seatLabel", render: (row) => row.seatLabel || "-" },
  { title: "房间", key: "roomNo", render: (row) => row.roomNo || "-" }
];
const queryHistoryColumns: DataTableColumns<PublicQueryHistory> = [
  {
    title: "查询时间",
    key: "createdAt",
    width: 190,
    fixed: "left",
    render: (row) => formatDateTimeLabel(row.createdAt)
  },
  {
    title: "类型",
    key: "queryType",
    width: 90,
    render: (row) => h(NTag, { size: "small", type: queryTypeTagType(row.queryType) }, { default: () => queryTypeLabel(row.queryType) })
  },
  {
    title: "状态",
    key: "queryStatus",
    width: 90,
    render: (row) => h(NTag, { size: "small", type: row.queryStatus === "SUCCESS" ? "success" : "error" }, { default: () => queryStatusLabel(row.queryStatus) })
  },
  {
    title: "输入条件",
    key: "queryValues",
    minWidth: 260,
    render: (row) => renderQueryValues(row.queryValues)
  },
  {
    title: "参会人",
    key: "attendeeName",
    minWidth: 150,
    render: (row) => attendeeLabel(row)
  },
  {
    title: "查询结果",
    key: "resultSummary",
    minWidth: 180,
    render: (row) => row.resultSummary || "-"
  },
  {
    title: "失败原因",
    key: "errorMessage",
    minWidth: 220,
    render: (row) => row.errorMessage || "-"
  }
];
const querySummaryColumns: DataTableColumns<PublicQuerySummary> = [
  {
    title: "分组",
    key: "groupKey",
    minWidth: 260,
    render(row) {
      const isAttendee = row.groupType === "ATTENDEE";
      return h("div", { class: "summary-person" }, [
        h(NTag, { size: "small", type: isAttendee ? "success" : "warning" }, { default: () => (isAttendee ? "已识别" : "未识别") }),
        h("span", isAttendee ? attendeeSummaryLabel(row) : row.failureReason || "未知失败")
      ]);
    }
  },
  { title: "查询次数", key: "totalCount", width: 100 },
  { title: "成功", key: "successCount", width: 90 },
  { title: "失败", key: "failedCount", width: 90 },
  {
    title: "最近查询",
    key: "latestQueryAt",
    width: 170,
    render: (row) => formatDateTimeLabel(row.latestQueryAt)
  },
  {
    title: "最近结果",
    key: "latestResult",
    minWidth: 220,
    render: (row) => row.latestResult || "-"
  }
];

function loadDetail() {
  // 新路由使用 meetingId；保留 id 兼容旧路由，便于主线程迁移 router 时平滑过渡。
  if (!meetingId.value) return;
  const targetMeetingId = meetingId.value;

  /*
   * 详情页只需要会议基础信息和公开查询配置。默认加载完整工作区会额外请求参会人、
   * 座位图、住宿、签到和统计接口，在网络面板里容易把 query-settings 排队拖住。
   * 这里显式关闭工作区加载，并让查询设置独立请求，避免无关模块影响详情页首屏。
   */
  void store.loadMeetingDetail(targetMeetingId, { loadWorkspace: false });
  loadQuerySettings(targetMeetingId);
}

function goBusinessPage(name: "meeting-registration") {
  if (!meetingId.value) return;
  void router.push({ name, params: { meetingId: meetingId.value } });
}

function loadQuerySettings(targetMeetingId = meetingId.value) {
  if (!targetMeetingId) return;
  void store.loadMeetingQuerySettings(targetMeetingId).then((settings) => {
    queryForm.seatQueryEnabled = settings.seatQueryEnabled;
    queryForm.roomQueryEnabled = settings.roomQueryEnabled;
    queryForm.dinnerQueryEnabled = settings.dinnerQueryEnabled;
    queryForm.checkinEnabled = settings.checkinEnabled;
    queryForm.selfCheckinEnabled = settings.selfCheckinEnabled;
    queryForm.queryPaused = settings.queryPaused;
    queryForm.fields = [...settings.fields];
    queryForm.matchMode = settings.matchMode;
    queryForm.allowMultipleResults = settings.allowMultipleResults;
    queryForm.combinedQueryTypes = [...settings.combinedQueryTypes];
    queryForm.pageTitle = settings.pageTitle;
    queryForm.pageDescription = settings.pageDescription || "";
    queryForm.announcement = settings.announcement || "";
    queryForm.publisher = settings.publisher || "";
    queryForm.startTime = settings.startTime || null;
    queryForm.endTime = settings.endTime || null;
    queryForm.buttonText = settings.buttonText;
    queryForm.emptyMessage = settings.emptyMessage;
    queryForm.seatNotice = settings.seatNotice;
    queryForm.roomNotice = settings.roomNotice;
    queryForm.dinnerNotice = settings.dinnerNotice;
    queryForm.pageContents = clonePageContents(settings.pageContents);
    queryForm.themeColor = settings.themeColor;
    queryForm.logoUrl = settings.logoUrl || "";
    queryForm.backgroundUrl = settings.backgroundUrl || "";
    queryForm.visibleResultFields = [...settings.visibleResultFields];
    queryForm.maskedResultFields = [...settings.maskedResultFields];
    queryForm.highlightResultFields = [...settings.highlightResultFields];
    queryForm.seatVisibleResultFields = [...settings.seatVisibleResultFields];
    queryForm.seatMaskedResultFields = [...settings.seatMaskedResultFields];
    queryForm.seatHighlightResultFields = [...settings.seatHighlightResultFields];
    queryForm.roomVisibleResultFields = [...settings.roomVisibleResultFields];
    queryForm.roomMaskedResultFields = [...settings.roomMaskedResultFields];
    queryForm.roomHighlightResultFields = [...settings.roomHighlightResultFields];
    queryForm.dinnerVisibleResultFields = [...settings.dinnerVisibleResultFields];
    queryForm.dinnerMaskedResultFields = [...settings.dinnerMaskedResultFields];
    queryForm.dinnerHighlightResultFields = [...settings.dinnerHighlightResultFields];
    fieldOptions.value = settings.fieldOptions;
    resultFieldOptions.value = settings.resultFieldOptions;
  }).catch((error) => {
    message.error(error instanceof Error ? error.message : "查询设置加载失败");
  });
}

function uploadLogoAsset(options: UploadCustomRequestOptions) {
  uploadQueryAsset(options, "logo");
}

function uploadBackgroundAsset(options: UploadCustomRequestOptions) {
  uploadQueryAsset(options, "background");
}

function uploadQueryAsset(options: UploadCustomRequestOptions, target: "logo" | "background") {
  const file = options.file.file;
  if (!file) {
    options.onError();
    message.error("请选择要上传的图片");
    return;
  }
  if (!isAllowedUploadImage(file)) {
    options.onError();
    message.error("仅支持 JPG、PNG、WebP 或 GIF 图片");
    return;
  }

  uploadingAsset.value = target;
  void store
    .uploadImageAsset(file)
    .then((result) => {
      if (target === "logo") {
        queryForm.logoUrl = result.url;
      } else {
        queryForm.backgroundUrl = result.url;
      }
      options.onFinish();
      message.success("图片已上传，链接已自动填入");
    })
    .catch((error) => {
      options.onError();
      message.error(error instanceof Error ? error.message : "图片上传失败");
    })
    .finally(() => {
      uploadingAsset.value = null;
    });
}

function loadQueryHistory(page = historyPagination.page) {
  if (!meetingId.value) return;
  loadingQueryHistory.value = true;
  void store
    .loadPublicQueryHistory(meetingId.value, {
      page: Math.max(page - 1, 0),
      size: historyPagination.pageSize,
      type: historyFilters.type,
      status: historyFilters.status,
      keyword: historyFilters.keyword
    })
    .then((result) => {
      queryHistoryRows.value = result.content;
      historyPagination.page = result.page + 1;
      historyPagination.pageSize = result.size;
      historyPagination.itemCount = result.totalElements;
    })
    .catch((error) => message.error(error instanceof Error ? error.message : "查询历史加载失败"))
    .finally(() => {
      loadingQueryHistory.value = false;
    });
}

function loadQuerySummary() {
  if (!meetingId.value) return;
  loadingQuerySummary.value = true;
  void store
    .loadPublicQuerySummary(meetingId.value)
    .then((items) => {
      querySummaryRows.value = items;
    })
    .catch((error) => message.error(error instanceof Error ? error.message : "查询汇总加载失败"))
    .finally(() => {
      loadingQuerySummary.value = false;
    });
}

function saveQuerySettings() {
  if (!meetingId.value || queryForm.fields.length === 0) {
    message.warning("请至少选择一个查询字段");
    return;
  }
  try {
    assertSafeImageUrl(queryForm.logoUrl, "Logo URL");
    assertSafeImageUrl(queryForm.backgroundUrl, "背景图 URL");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "图片地址不安全");
    return;
  }
  savingQuerySettings.value = true;
  const pageContents = clonePageContents(queryForm.pageContents);
  const combinedContent = pageContents.combined;
  const resultCheckinEnabled = resultCheckinPageKeys.some((key) => pageContents[key].checkinEnabled !== false);
  void store
    .saveMeetingQuerySettings(meetingId.value, {
      seatQueryEnabled: queryForm.seatQueryEnabled,
      roomQueryEnabled: queryForm.roomQueryEnabled,
      dinnerQueryEnabled: queryForm.dinnerQueryEnabled,
      checkinEnabled: resultCheckinEnabled,
      selfCheckinEnabled: queryForm.selfCheckinEnabled,
      queryPaused: queryForm.queryPaused,
      fields: queryForm.fields,
      matchMode: queryForm.matchMode,
      allowMultipleResults: queryForm.allowMultipleResults,
      combinedQueryTypes: queryForm.combinedQueryTypes,
      pageTitle: combinedContent.title || queryForm.pageTitle,
      pageDescription: combinedContent.description ?? queryForm.pageDescription,
      announcement: combinedContent.announcement ?? queryForm.announcement,
      publisher: combinedContent.publisher ?? queryForm.publisher,
      startTime: queryForm.startTime || undefined,
      endTime: queryForm.endTime || undefined,
      buttonText: combinedContent.buttonText || queryForm.buttonText,
      emptyMessage: combinedContent.emptyMessage || queryForm.emptyMessage,
      seatNotice: pageContents.seat.notice ?? queryForm.seatNotice,
      roomNotice: pageContents.room.notice ?? queryForm.roomNotice,
      dinnerNotice: pageContents.dinner.notice ?? queryForm.dinnerNotice,
      pageContents,
      themeColor: queryForm.themeColor,
      logoUrl: queryForm.logoUrl,
      backgroundUrl: queryForm.backgroundUrl,
      resultLayout: "CARD",
      visibleResultFields: queryForm.visibleResultFields,
      maskedResultFields: queryForm.maskedResultFields,
      highlightResultFields: queryForm.highlightResultFields,
      seatVisibleResultFields: queryForm.seatVisibleResultFields,
      seatMaskedResultFields: queryForm.seatMaskedResultFields,
      seatHighlightResultFields: queryForm.seatHighlightResultFields,
      roomVisibleResultFields: queryForm.roomVisibleResultFields,
      roomMaskedResultFields: queryForm.roomMaskedResultFields,
      roomHighlightResultFields: queryForm.roomHighlightResultFields,
      dinnerVisibleResultFields: queryForm.dinnerVisibleResultFields,
      dinnerMaskedResultFields: queryForm.dinnerMaskedResultFields,
      dinnerHighlightResultFields: queryForm.dinnerHighlightResultFields
    })
    .then(() => message.success("查询设置已保存"))
    .catch((error) => message.error(error instanceof Error ? error.message : "查询设置保存失败"))
    .finally(() => {
      savingQuerySettings.value = false;
    });
}

function clonePageContents(contents: Record<PublicPageKey, PublicPageContent>): Record<PublicPageKey, PublicPageContent> {
  return (Object.keys(defaultPageContents) as PublicPageKey[]).reduce(
    (result, key) => {
      result[key] = { ...defaultPageContents[key], ...(contents[key] || {}) };
      return result;
    },
    {} as Record<PublicPageKey, PublicPageContent>
  );
}

function runAdminQuery(type: "seat" | "room") {
  if (!meetingId.value) return;
  testQueryType.value = type;
  adminQueryTouched.value = true;
  testingQuery.value = true;
  void store
    .adminQueryCandidates(meetingId.value, type, queryValues)
    .then((items) => {
      queryResults.value = items;
      if (!items.length) message.warning("没有匹配的报名记录");
    })
    .catch((error) => message.error(error instanceof Error ? error.message : "查询失败"))
    .finally(() => {
      testingQuery.value = false;
    });
}

function copyPublicQueryUrl(url: string, label: string) {
  void navigator.clipboard
    .writeText(url)
    .then(() => message.success(`${label}已复制`))
    .catch(() => message.error("复制失败，请手动复制链接"));
}

function openPublicQueryUrl(url: string) {
  window.open(url, "_blank", "noopener,noreferrer");
}

async function refreshQueryQrcodes() {
  const token = ++qrRenderToken;
  const entry = activePublicQueryLink.value;
  if (!entry) {
    queryLinkQrcodes.value = {};
    return;
  }
  /*
   * 只生成当前正在预览的入口二维码。
   * 旧实现会在详情页首屏一次性生成多个 DataURL；在快速切页或浏览器刚解析完主包时，
   * 这类同步计算会和 Naive UI 表单、Tabs、DataTable 的挂载挤在同一主线程窗口里。
   * 这里保留已有二维码缓存，并通过 token 丢弃过期任务，避免用户切换入口时写回旧结果。
   */
  const { default: QRCode } = await import("qrcode");
  if (token !== qrRenderToken) return;
  const qrCode = await QRCode.toDataURL(entry.url, {
    width: 132,
    margin: 1,
    color: {
      dark: "#1f2937",
      light: "#ffffff"
    }
  });
  if (token !== qrRenderToken) return;
  queryLinkQrcodes.value = {
    ...queryLinkQrcodes.value,
    [entry.key]: qrCode
  };
}

function scheduleRefreshQueryQrcodes() {
  const requestIdleCallback = window.requestIdleCallback as ((handler: () => void, options?: { timeout?: number }) => number) | undefined;
  const cancelIdleCallback = window.cancelIdleCallback as ((handle: number) => void) | undefined;
  if (qrIdleHandle !== undefined) {
    if (cancelIdleCallback) cancelIdleCallback(qrIdleHandle);
    else window.clearTimeout(qrIdleHandle);
  }
  qrIdleHandle = requestIdleCallback
    ? requestIdleCallback(() => {
        qrIdleHandle = undefined;
        void refreshQueryQrcodes();
      }, { timeout: 2000 })
    : window.setTimeout(() => {
        qrIdleHandle = undefined;
        void refreshQueryQrcodes();
      }, 500);
}

function fieldLabel(field: PublicQueryField) {
  return fieldOptions.value.find((item) => item.field === field)?.label || field;
}

function resultFieldLabel(field: PublicQueryResultField) {
  return resultFieldOptions.value.find((item) => item.field === field)?.label || field;
}

function setResultFieldsIfChanged(target: PublicQueryResultField[], next: PublicQueryResultField[]) {
  if (target.length === next.length && target.every((field, index) => field === next[index])) return target;
  return next;
}

function moveResultField(fields: PublicQueryResultField[], index: number, offset: -1 | 1) {
  const targetIndex = index + offset;
  if (targetIndex < 0 || targetIndex >= fields.length) return fields;
  const next = [...fields];
  const [field] = next.splice(index, 1);
  next.splice(targetIndex, 0, field);
  return next;
}

function moveSeatVisibleResultField(index: number, offset: -1 | 1) {
  queryForm.seatVisibleResultFields = moveResultField(queryForm.seatVisibleResultFields, index, offset);
}

function moveRoomVisibleResultField(index: number, offset: -1 | 1) {
  queryForm.roomVisibleResultFields = moveResultField(queryForm.roomVisibleResultFields, index, offset);
}

function moveDinnerVisibleResultField(index: number, offset: -1 | 1) {
  queryForm.dinnerVisibleResultFields = moveResultField(queryForm.dinnerVisibleResultFields, index, offset);
}

function combinedQueryTypeEnabled(type: CombinedQueryType) {
  return queryForm.combinedQueryTypes.includes(type);
}

function setCombinedQueryType(type: CombinedQueryType, enabled: boolean) {
  const current = new Set(queryForm.combinedQueryTypes);
  if (enabled) {
    current.add(type);
  } else {
    current.delete(type);
  }
  /*
   * 按固定顺序回写，避免用户反复切换时数组顺序变化，导致保存 payload 和脏检查不稳定。
   */
  queryForm.combinedQueryTypes = (["SEAT", "ROOM", "DINNER"] as CombinedQueryType[]).filter((item) => current.has(item));
}

function setCombinedSeatQueryType(enabled: boolean) {
  setCombinedQueryType("SEAT", enabled);
}

function setCombinedRoomQueryType(enabled: boolean) {
  setCombinedQueryType("ROOM", enabled);
}

function setCombinedDinnerQueryType(enabled: boolean) {
  setCombinedQueryType("DINNER", enabled);
}

function syncResultFieldSelections() {
  const seatVisible = new Set(queryForm.seatVisibleResultFields);
  const roomVisible = new Set(queryForm.roomVisibleResultFields);
  const dinnerVisible = new Set(queryForm.dinnerVisibleResultFields);
  const seatMaskedResultFields = queryForm.seatMaskedResultFields.filter((field) => seatVisible.has(field));
  const seatHighlightResultFields = queryForm.seatHighlightResultFields.filter((field) => seatVisible.has(field));
  const roomMaskedResultFields = queryForm.roomMaskedResultFields.filter((field) => roomVisible.has(field));
  const roomHighlightResultFields = queryForm.roomHighlightResultFields.filter((field) => roomVisible.has(field));
  const dinnerMaskedResultFields = queryForm.dinnerMaskedResultFields.filter((field) => dinnerVisible.has(field));
  const dinnerHighlightResultFields = queryForm.dinnerHighlightResultFields.filter((field) => dinnerVisible.has(field));

  /*
   * 这个同步函数由 deep watch 触发，回写同一个被监听的数组时必须先做内容相等判断。
   * 如果每次都创建新数组并赋值，Vue 会继续触发 watcher，详情页拿到查询设置后会进入
   * 响应式更新循环，表现为浏览器“此页面没有响应”，而网络请求看起来一直 pending。
   */
  queryForm.seatMaskedResultFields = setResultFieldsIfChanged(queryForm.seatMaskedResultFields, seatMaskedResultFields);
  queryForm.seatHighlightResultFields = setResultFieldsIfChanged(queryForm.seatHighlightResultFields, seatHighlightResultFields);
  queryForm.roomMaskedResultFields = setResultFieldsIfChanged(queryForm.roomMaskedResultFields, roomMaskedResultFields);
  queryForm.roomHighlightResultFields = setResultFieldsIfChanged(queryForm.roomHighlightResultFields, roomHighlightResultFields);
  queryForm.dinnerMaskedResultFields = setResultFieldsIfChanged(queryForm.dinnerMaskedResultFields, dinnerMaskedResultFields);
  queryForm.dinnerHighlightResultFields = setResultFieldsIfChanged(queryForm.dinnerHighlightResultFields, dinnerHighlightResultFields);
  queryForm.visibleResultFields = setResultFieldsIfChanged(
    queryForm.visibleResultFields,
    Array.from(new Set([...queryForm.seatVisibleResultFields, ...queryForm.roomVisibleResultFields, ...queryForm.dinnerVisibleResultFields]))
  );
  queryForm.maskedResultFields = setResultFieldsIfChanged(
    queryForm.maskedResultFields,
    Array.from(new Set([...queryForm.seatMaskedResultFields, ...queryForm.roomMaskedResultFields, ...queryForm.dinnerMaskedResultFields]))
  );
  queryForm.highlightResultFields = setResultFieldsIfChanged(
    queryForm.highlightResultFields,
    Array.from(new Set([...queryForm.seatHighlightResultFields, ...queryForm.roomHighlightResultFields, ...queryForm.dinnerHighlightResultFields]))
  );
}

function queryTypeLabel(type: PublicQueryType) {
  if (type === "SEAT") return "座位";
  if (type === "ROOM") return "住宿";
  if (type === "DINNER") return "晚宴";
  return "签到";
}

function queryTypeTagType(type: PublicQueryType) {
  if (type === "SEAT") return "info";
  if (type === "ROOM") return "warning";
  if (type === "DINNER") return "success";
  return "success";
}

function queryStatusLabel(status: PublicQueryStatus) {
  return status === "SUCCESS" ? "成功" : "失败";
}

function attendeeLabel(row: PublicQueryHistory) {
  if (!row.attendeeName && !row.attendeePhone) return "-";
  return [row.attendeeName, row.attendeePhone].filter(Boolean).join(" / ");
}

function attendeeSummaryLabel(row: PublicQuerySummary) {
  if (!row.attendeeName && !row.attendeePhone) return "未知参会人";
  return [row.attendeeName, row.attendeePhone].filter(Boolean).join(" / ");
}

function renderQueryValues(values: Record<string, unknown>) {
  const entries = Object.entries(values || {}).filter(([, value]) => value !== undefined && value !== null && String(value).trim() !== "");
  if (!entries.length) return "-";
  return h("div", { class: "query-value-list" }, entries.map(([key, value]) => h("span", `${fieldLabel(key as PublicQueryField)}：${String(value)}`)));
}

function handleHistoryPageChange(page: number) {
  loadQueryHistory(page);
}

function handleHistoryPageSizeChange(pageSize: number) {
  historyPagination.pageSize = pageSize;
  loadQueryHistory(1);
}

onMounted(loadDetail);
onUnmounted(() => {
  qrRenderToken++;
  if (qrIdleHandle !== undefined) {
    const cancelIdleCallback = window.cancelIdleCallback as ((handle: number) => void) | undefined;
    if (cancelIdleCallback) cancelIdleCallback(qrIdleHandle);
    else window.clearTimeout(qrIdleHandle);
    qrIdleHandle = undefined;
  }
});
watch(meetingId, loadDetail);
watch(currentSection, (value) => {
  if (value === "history") {
    loadQueryHistory(1);
    loadQuerySummary();
  }
}, { immediate: true });
watch(historyView, (value) => {
  if (currentSection.value !== "history") return;
  if (value === "logs" && !queryHistoryRows.value.length) loadQueryHistory(1);
  if (value === "summary" && !querySummaryRows.value.length) loadQuerySummary();
});
watch(() => [historyFilters.type, historyFilters.status], () => {
  if (currentSection.value === "history" && historyView.value === "logs") loadQueryHistory(1);
});
watch(
  () => [
    queryForm.seatVisibleResultFields,
    queryForm.seatMaskedResultFields,
    queryForm.seatHighlightResultFields,
    queryForm.roomVisibleResultFields,
    queryForm.roomMaskedResultFields,
    queryForm.roomHighlightResultFields,
    queryForm.dinnerVisibleResultFields,
    queryForm.dinnerMaskedResultFields,
    queryForm.dinnerHighlightResultFields
  ],
  syncResultFieldSelections,
  { deep: true }
);
watch(publicQueryLinkUrls, () => {
  queryLinkQrcodes.value = {};
}, { immediate: true });
watch(activePublicQueryLinkUrl, (url) => {
  if (url && activePublicQueryLink.value && !queryLinkQrcodes.value[activePublicQueryLink.value.key]) {
    scheduleRefreshQueryQrcodes();
  }
}, { immediate: true });
</script>

<style scoped>
.detail-card {
  margin-top: 16px;
}

.query-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.query-card-header h2,
.query-card-header h3 {
  margin: 0;
}

.query-card-header span {
  color: var(--text-secondary);
}

.query-card-header.compact {
  margin-bottom: 12px;
}

.query-settings-workspace {
  display: grid;
  min-width: 0;
}

.settings-pane {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.settings-pane-grid {
  grid-template-columns: repeat(2, minmax(320px, 1fr));
  align-items: start;
}

.content-settings-pane {
  grid-template-columns: 1fr;
  gap: 18px;
  align-items: start;
}

.entry-settings-grid {
  grid-template-columns: minmax(500px, 1.35fr) minmax(300px, 0.75fr);
}

.settings-panel {
  display: grid;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.settings-panel-wide {
  grid-column: 1 / -1;
}

.entry-switch-panel {
  align-content: start;
}

.entry-toggle-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  border: 0;
  border-radius: 0;
  overflow: visible;
}

.entry-toggle-grid .toggle-row {
  min-height: 76px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.entry-toggle-grid .toggle-row + .toggle-row {
  border-top: 1px solid var(--border-color);
}

.entry-toggle-grid .toggle-row > div {
  min-width: 0;
}

.entry-toggle-grid .toggle-row strong {
  display: block;
  margin-bottom: 3px;
}

.entry-status-panel .status-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  border: 0;
  overflow: visible;
  background: transparent;
}

.entry-status-panel .status-row {
  min-height: 62px;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #ffffff;
}

.entry-status-panel .status-row + .status-row {
  border-top: 1px solid var(--border-color);
}

.page-content-panel,
.query-rules-panel,
.entry-status-panel,
.access-control-panel {
  grid-column: auto;
  grid-row: auto;
}

.query-rules-panel,
.page-content-panel,
.result-fields-panel {
  gap: 16px;
}

.query-rules-form {
  grid-template-columns: minmax(300px, 1fr) minmax(220px, 0.55fr) minmax(280px, 0.75fr);
  align-items: end;
  gap: 14px;
}

.query-rules-form .compact-toggle-row {
  min-height: 58px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.page-content-form {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 14px;
  align-items: start;
}

.content-title-field,
.content-two-column-row,
.content-full-field {
  grid-column: 1 / -1;
}

.content-page-tabs {
  min-width: 0;
}

.content-page-tabs :deep(.n-tabs-nav) {
  padding: 3px;
  border-radius: 8px;
  background: #f8fafc;
}

.content-page-tabs :deep(.n-tabs-tab) {
  min-height: 32px;
}

.result-appearance-panel {
  grid-column: 1 / -1;
  grid-template-columns: minmax(180px, 0.22fr) minmax(0, 1fr);
  align-items: center;
  padding: 14px 16px;
}

.result-appearance-form {
  min-width: 0;
  grid-template-columns: minmax(360px, 0.85fr) minmax(0, 1.15fr);
  gap: 12px;
  align-items: start;
}

.result-appearance-form > .query-form-row {
  min-width: 0;
}

.result-appearance-form > .query-form-row:first-child {
  grid-template-columns: minmax(130px, 0.55fr) minmax(210px, 0.9fr);
}

.result-field-config-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.result-field-config {
  display: grid;
  gap: 12px;
  align-content: start;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #ffffff;
}

.result-field-config .query-form {
  gap: 10px;
}

.result-field-config :deep(.n-base-selection-label) {
  min-height: 36px;
}

.result-field-config :deep(.n-base-select-tags) {
  row-gap: 4px;
}

.field-order-list {
  display: grid;
  gap: 8px;
  width: 100%;
}

.field-order-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 36px;
  padding: 7px 8px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #f8fafc;
}

.field-order-index {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 999px;
  background: #e8eef7;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.field-order-row strong {
  min-width: 0;
  color: var(--text-primary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-order-actions {
  flex: 0 0 auto;
}

.section-heading {
  display: grid;
  gap: 3px;
}

.compact-heading {
  gap: 2px;
}

.entry-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.entry-panel-heading > div {
  min-width: 0;
}

.settings-panel h3 {
  margin: 0;
}

.section-heading strong,
.entry-copy strong {
  color: var(--text-primary);
}

.section-heading span,
.entry-panel-heading span,
.toggle-row span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 18px;
}

.toggle-list {
  display: grid;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 68px;
  padding: 12px 14px;
  background: #ffffff;
}

.toggle-row + .toggle-row {
  border-top: 1px solid var(--border-color);
}

.query-form {
  display: grid;
  gap: 10px;
}

.query-form :deep(.n-form-item) {
  margin-bottom: 0;
}

.query-form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.theme-color-field {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.theme-color-field :deep(.n-color-picker) {
  width: 44px;
}

.theme-color-field :deep(.n-color-picker-trigger) {
  width: 44px;
  min-width: 44px;
  height: 34px;
  padding: 4px;
}

.theme-color-field :deep(.n-color-picker-trigger__value) {
  display: none;
}

.theme-color-field :deep(.n-color-picker-checkboard),
.theme-color-field :deep(.n-color-picker-trigger__fill) {
  width: 100%;
  height: 100%;
}

.asset-url-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.asset-fields-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 1180px) {
  .content-settings-pane {
    grid-template-columns: 1fr;
  }

  .entry-toggle-grid {
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  }

  .query-rules-form,
  .page-content-form,
  .combined-type-switches {
    grid-template-columns: 1fr;
  }

  .page-content-panel,
  .query-rules-panel,
  .entry-status-panel,
  .access-control-panel,
  .result-appearance-panel {
    grid-column: 1 / -1;
    grid-row: auto;
  }

  .result-appearance-panel {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .result-appearance-form {
    grid-template-columns: 1fr;
  }

  .result-field-config-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.compact-toggle-list .toggle-row {
  min-height: 58px;
}

.combined-type-switches {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}

@media (max-width: 1180px) {
  .combined-type-switches {
    grid-template-columns: 1fr;
  }
}

.compact-toggle-row {
  min-height: 54px;
  padding: 12px 14px;
}

.page-checkin-toggle {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.entry-switch {
  flex: 0 0 auto;
}

.entry-preview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 136px;
  gap: 18px;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #ffffff;
}

.entry-copy {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.entry-copy p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.qr-box {
  width: 136px;
  height: 136px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #ffffff;
  color: var(--text-secondary);
  font-size: 12px;
}

.qr-box img {
  width: 120px;
  height: 120px;
  display: block;
}

.rule-note {
  padding: 10px 12px;
  border-radius: 8px;
  background: #ffffff;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.status-list {
  display: grid;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 46px;
  padding: 10px 12px;
}

.status-row + .status-row {
  border-top: 1px solid var(--border-color);
}

.status-row span {
  color: var(--text-secondary);
  font-size: 13px;
}

.admin-query-placeholder {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.admin-query-content {
  display: grid;
  gap: 12px;
  padding-top: 2px;
}

.admin-query-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
}

.query-test-fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
}

.admin-query-placeholder {
  display: grid;
  min-height: 72px;
  place-items: center;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.query-history-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  min-width: 0;
}

.query-history-panel > * {
  min-width: 0;
  max-width: 100%;
}

.query-history-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.history-filters {
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 136px 136px minmax(260px, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.history-filters .n-button {
  min-width: 64px;
}

.history-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 2px;
}

.query-value-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.query-value-list span {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 2px 7px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--surface-muted);
}

.summary-person {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.summary-person span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .content-page-tabs :deep(.n-tabs-nav) {
    overflow-x: auto;
  }

  .content-page-tabs :deep(.n-tabs-nav-scroll-content) {
    width: max-content;
    min-width: 100%;
  }

  .content-page-tabs :deep(.n-tabs-tab) {
    min-width: max-content;
  }

	  .settings-pane-grid,
	  .entry-settings-grid,
	  .entry-toggle-grid,
	  .entry-status-panel .status-list,
	  .result-field-config-grid {
	    grid-template-columns: 1fr;
	  }

  .result-appearance-panel,
  .asset-fields-row {
    grid-template-columns: 1fr;
  }

  .query-card-header,
  .section-heading,
  .entry-panel-heading,
  .query-history-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .history-filters {
    grid-template-columns: 1fr;
  }

  .history-pagination {
    justify-content: flex-start;
  }

  .entry-switch {
    width: 100%;
  }

  .entry-preview {
    grid-template-columns: 1fr;
  }

  .admin-query-toolbar {
    grid-template-columns: 1fr;
  }

  .query-form-row,
  .theme-color-field,
  .asset-url-field {
    grid-template-columns: 1fr;
  }

  .qr-box {
    width: 132px;
    height: 132px;
  }

  .qr-box img {
    width: 116px;
    height: 116px;
  }

  .query-history-panel :deep(.n-data-table),
  .admin-query-content :deep(.n-data-table) {
    min-width: 720px;
  }

  .query-history-panel,
  .admin-query-content {
    overflow-x: auto;
  }
}
</style>
