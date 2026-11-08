// 此工厂会被部署补丁嵌入 TDuck 生成器，仅对启用了联想的单位名称字段生效。
function createOrganizationInputMixin(runtime) {
  var transport = runtime && runtime.fetch ? runtime.fetch : fetch;
  var unavailableMessage = '单位查询暂不可用，可继续手工填写';

  function requestJson(url, options, onController) {
    var controller = typeof AbortController === 'function' ? new AbortController() : null;
    if (onController) onController(controller);
    return new Promise(function (resolve, reject) {
      var timeout = setTimeout(function () {
        if (controller) controller.abort();
        reject(new Error('单位查询超时'));
      }, 3500);
      transport(url, Object.assign({
        credentials: 'same-origin', cache: 'no-store', signal: controller ? controller.signal : undefined
      }, options)).then(function (response) {
        if (!response.ok) throw new Error('单位查询失败');
        return response.json();
      }).then(function (result) {
        clearTimeout(timeout);
        resolve(result);
      }, function (error) {
        clearTimeout(timeout);
        reject(error);
      });
    });
  }

  return {
    data: function () {
      return {
        meetingOsOrganizationConfig: null,
        meetingOsOrganizationConfigPromise: null,
        meetingOsOrganizationConfigExpiresAt: 0,
        meetingOsOrganizationGeneration: 0,
        meetingOsOrganizationAbort: null,
        meetingOsOrganizationMessage: ''
      };
    },
    computed: {
      meetingOsOrganizationLookupEnabled: function () {
        var config = this.item && this.item.config;
        return Boolean(config && config.dataType && config.dataType.type === 'organization'
          && config.meetingOsTianyanchaEnabled === true);
      }
    },
    watch: {
      changeValue: function () { this.cancelMeetingOsOrganizationQuery(); },
      meetingOsOrganizationLookupEnabled: function () { this.cancelMeetingOsOrganizationQuery(); }
    },
    beforeDestroy: function () { this.cancelMeetingOsOrganizationQuery(); },
    methods: {
      cancelMeetingOsOrganizationQuery: function () {
        this.meetingOsOrganizationGeneration += 1;
        if (this.meetingOsOrganizationAbort) this.meetingOsOrganizationAbort.abort();
        this.meetingOsOrganizationAbort = null;
        this.meetingOsOrganizationMessage = '';
        var input = this.$refs.meetingOsOrganizationInput;
        if (input) {
          input.suggestions = [];
          input.highlightedIndex = -1;
        }
      },
      meetingOsOrganizationFormKey: function () {
        var route = this.$route || {};
        var key = (route.query || {}).key || (route.params || {}).key;
        if (key) return String(key);
        for (var parent = this.$parent; parent; parent = parent.$parent) {
          var config = parent.formConfig || parent.formConf || {};
          if (parent.formKey || config.formKey) return String(parent.formKey || config.formKey);
        }
        return '';
      },
      loadMeetingOsOrganizationConfig: function () {
        var self = this;
        if (this.meetingOsOrganizationConfigExpiresAt > Date.now()) {
          return Promise.resolve(this.meetingOsOrganizationConfig);
        }
        if (this.meetingOsOrganizationConfigPromise) return this.meetingOsOrganizationConfigPromise;
        var formKey = this.meetingOsOrganizationFormKey();
        if (!formKey) return Promise.resolve(null);
        this.meetingOsOrganizationConfigPromise = requestJson(
          '/api/public/tduck/organization-lookup-token?formKey=' + encodeURIComponent(formKey),
          { method: 'GET', headers: { Accept: 'application/json' } }
        ).then(function (payload) {
          var config = payload && payload.success === true ? payload.data : null;
          self.meetingOsOrganizationConfig = config;
          var ttl = config && config.enabled ? Math.max(1, Math.min(600, Number(config.expiresInSeconds) || 0) - 5) : 30;
          self.meetingOsOrganizationConfigExpiresAt = Date.now() + ttl * 1000;
          return config;
        }).finally(function () { self.meetingOsOrganizationConfigPromise = null; });
        return this.meetingOsOrganizationConfigPromise;
      },
      fetchMeetingOsOrganizationSuggestions: async function (query, callback) {
        var self = this;
        var word = String(query || '').trim();
        var generation = ++this.meetingOsOrganizationGeneration;
        function current() {
          return !self._isDestroyed && !self._isBeingDestroyed && self.meetingOsOrganizationLookupEnabled
            && self.meetingOsOrganizationGeneration === generation && String(self.changeValue || '').trim() === word;
        }
        if (!this.meetingOsOrganizationLookupEnabled || this.item.readonly || this.item.disabled
            || word.length < 3 || word.length > 120) {
          callback([]);
          return;
        }
        try {
          var config = await this.loadMeetingOsOrganizationConfig();
          if (!current()) return;
          if (!config || !config.enabled || !config.token) {
            this.meetingOsOrganizationMessage = unavailableMessage;
            callback([]);
            return;
          }
          var payload = await requestJson('/api/public/tduck/organization-suggestions', {
            method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ formKey: this.meetingOsOrganizationFormKey(), token: config.token, word: word })
          }, function (controller) { self.meetingOsOrganizationAbort = controller; });
          // 旧请求晚返回、用户清空或选中后，不得覆盖当前输入的候选。
          if (!current()) return;
          var result = payload && payload.success === true ? payload.data : null;
          if (result && result.code === 'INVALID_TOKEN') this.meetingOsOrganizationConfigExpiresAt = 0;
          var available = result && result.available === true && Array.isArray(result.items);
          this.meetingOsOrganizationMessage = available ? '' : unavailableMessage;
          callback(available ? result.items.filter(function (item) { return item && typeof item.name === 'string'; }) : []);
        } catch (error) {
          if (current()) {
            this.meetingOsOrganizationMessage = unavailableMessage;
            callback([]);
          }
        } finally {
          if (current()) this.meetingOsOrganizationAbort = null;
        }
      },
      renderMeetingOsOrganizationInput: function () {
        var self = this;
        var h = this.$createElement;
        var attributes = Object.assign({}, this.item);
        delete attributes.config;
        delete attributes.style;
        var slots = [];
        if (this.item.prepend) slots.push(h('span', { slot: 'prefix' }, this.item.prepend));
        if (this.item.append) slots.push(h('span', { slot: 'suffix' }, this.item.append));
        return h('div', [h('el-autocomplete', {
          ref: 'meetingOsOrganizationInput', style: Object.assign({ width: '100%' }, this.item.style), attrs: attributes,
          props: {
            value: String(this.changeValue || ''), valueKey: 'name', size: 'medium', debounce: 500,
            fetchSuggestions: this.fetchMeetingOsOrganizationSuggestions,
            triggerOnFocus: false, highlightFirstItem: false, selectWhenUnmatched: false
          },
          on: { input: function (value) { self.changeValue = value; } },
          scopedSlots: { default: function (scope) {
            var entry = scope.item;
            return [h('div', { style: { whiteSpace: 'normal', lineHeight: '22px', padding: '6px 0' } }, entry.name)];
          } }
        }, slots), this.meetingOsOrganizationMessage ? h('div', {
          attrs: { role: 'status', 'aria-live': 'polite' },
          style: { color: '#909399', fontSize: '12px', lineHeight: '20px', marginTop: '4px' }
        }, this.meetingOsOrganizationMessage) : null]);
      }
    }
  };
}

module.exports = { createOrganizationInputMixin };
