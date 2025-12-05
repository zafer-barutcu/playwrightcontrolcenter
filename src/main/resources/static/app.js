const state = {
      scenarios: {},
      order: [],
      counts: { pass: 0, fail: 0 },
      metaCounts: null,
      runId: null,
      source: null,
      sparkData: []
    };

    const systemProps = ["env","baseUrl","browserName","headless","cucumber.execution.parallel.enabled","cucumber.execution.parallel.config.strategy","cucumber.execution.parallel.config.fixed.parallelism","videoMode","cucumber.filter.name","cucumber.filter.tags"];

    const normStatus = (st) => (st || "").trim().toUpperCase();
    const isFailStatus = (st) => /FAIL/.test(normStatus(st));
    const isPassStatus = (st) => /PASS/.test(normStatus(st));

    function ensureProp(cmd, key, val) {
      const tokens = cmd.trim().split(/\s+/).filter(Boolean);
      const prefix = `-D${key}=`;
      const filtered = tokens.filter(t => !t.startsWith(prefix));
      let safeVal = val ?? "";
      if (typeof safeVal === "string" && (safeVal.includes(" ") || safeVal.includes("|"))) {
        safeVal = `"${safeVal.replace(/"/g, '\\"')}"`;
      }
      filtered.push(`${prefix}${safeVal}`);
      return filtered.join(" ");
    }

    function buildCommandPreview() {
      const baseCmd = document.getElementById("command").value || "mvn test";
      let cmd = baseCmd;
      cmd = ensureProp(cmd, "env", document.getElementById("env").value);
      cmd = ensureProp(cmd, "baseUrl", document.getElementById("baseUrl").value);
      cmd = ensureProp(cmd, "browserName", document.getElementById("browser").value);
      cmd = ensureProp(cmd, "headless", document.getElementById("headless").value);
      cmd = ensureProp(cmd, "cucumber.execution.parallel.enabled", "true");
      cmd = ensureProp(cmd, "cucumber.execution.parallel.config.strategy", "fixed");
      cmd = ensureProp(cmd, "cucumber.execution.parallel.config.fixed.parallelism", document.getElementById("parallelism").value);
      cmd = ensureProp(cmd, "videoMode", document.getElementById("videoMode").value);
      const suiteVal = document.getElementById("suiteSelect")?.value?.trim();
    const suiteOption = document.getElementById("suiteSelect")?.selectedOptions?.[0];
    const suiteName = suiteOption?.textContent?.trim() || "";
    const namePrefix = "-Dcucumber.filter.name=";
    if (suiteName) {
      if (suiteVal) {
        cmd = ensureProp(cmd, "cucumber.filter.name", suiteVal);
      } else {
        cmd = cmd.split(/\s+/).filter(t => !t.startsWith(namePrefix)).join(" ");
      }
    } else {
      cmd = cmd.split(/\s+/).filter(t => !t.startsWith(namePrefix)).join(" ");
    }
      const el = document.getElementById("commandPreview");
      if (el) el.textContent = cmd;
      return cmd;
    }

    let previewOpen = false;
    function toggleCommandPreview() {
      previewOpen = !previewOpen;
      const body = document.getElementById("cmdPreviewBody");
      const arrow = document.getElementById("cmdPreviewArrow");
      if (body) body.style.display = previewOpen ? "flex" : "none";
      if (arrow) arrow.style.transform = previewOpen ? "rotate(0deg)" : "rotate(-90deg)";
    }

    function updateClock() {
      const el = document.getElementById("clock");
      if (!el) return;
      const now = new Date();
      const date = now.toLocaleDateString("en-GB", { weekday: "short", day: "2-digit", month: "short" });
      const time = now.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
      el.textContent = `${date} · ${time}`;
    }

    function resetUI(status = "Idle") {
      state.scenarios = {};
      state.order = [];
      state.counts = { pass: 0, fail: 0 };
      state.metaCounts = null;
      state.runId = null;
      state.sparkData = [];
      document.getElementById("total").textContent = 0;
      document.getElementById("passed").textContent = 0;
      document.getElementById("failed").textContent = 0;
      document.getElementById("running").textContent = 0;
      document.getElementById("scenarios").innerHTML = "";
      document.getElementById("status").textContent = status;
      document.getElementById("runMeta").textContent = "Waiting";
      document.getElementById("currentRunLabel").textContent = "No run";
      document.getElementById("scenarioCountLabel").textContent = "0 items";
      document.getElementById("donut").style.background = "conic-gradient(var(--pass) 0deg, var(--pass) 0deg, var(--fail) 0deg, var(--fail) 0deg, rgba(255,255,255,0.08) 0deg)";
      document.getElementById("donutLabel").textContent = "0%";
      document.getElementById("spark").innerHTML = "";
      document.getElementById("log").textContent = "";
    }

    function ensureScenario(name) {
      if (!state.scenarios[name]) {
        state.scenarios[name] = { status: "RUNNING", steps: [], videoUrl: null, open: false, name };
        state.order.push(name);
      }
    }

    function computeCounts() {
      let pass = 0, fail = 0;
      state.order.forEach(name => {
        const s = state.scenarios[name];
        if (!s) return;
        if ((s.status || "").toUpperCase() === "FAIL") fail++;
        else if ((s.status || "").toUpperCase() === "PASS") pass++;
      });
      state.counts = { pass, fail };
    }

    function currentCounts() {
      if (state.metaCounts) {
        return {
          total: Math.max(state.metaCounts.total, 0),
          pass: Math.max(state.metaCounts.pass, 0),
          fail: Math.max(state.metaCounts.fail, 0)
        };
      }
      const total = state.order.length;
      const seen = new Set();
      let pass = 0, fail = 0;
      state.order.forEach(n => {
        if (seen.has(n)) return;
        seen.add(n);
        const st = (state.scenarios[n]?.status || "").toUpperCase();
        if (st === "PASS") pass++;
        else if (st === "FAIL") fail++;
      });
      return { total: Math.max(total, 0), pass: Math.max(pass, 0), fail: Math.max(fail, 0) };
    }

    function renderDonut() {
      const { total, pass, fail } = currentCounts();
      const denom = total || 1;
      const passDeg = (pass / total) * 360;
      const failDeg = ((pass + fail) / total) * 360;
      const donut = document.getElementById("donut");
      donut.style.background = `conic-gradient(var(--pass) 0deg, var(--pass) ${passDeg}deg, var(--fail) ${passDeg}deg, var(--fail) ${failDeg}deg, rgba(255,255,255,0.08) ${failDeg}deg)`;
      const percent = Math.round((pass / Math.max(pass + fail, 1)) * 100);
      document.getElementById("donutLabel").textContent = `${percent}%`;
    }

    function renderSpark() {
      const container = document.getElementById("spark");
      container.innerHTML = "";
      const max = Math.max(...state.sparkData.map(v => Math.abs(v)), 1);
      const width = container.clientWidth || 260;
      const total = Math.max(currentCounts().total, 10);
      const barWidth = Math.max(4, Math.min(10, Math.floor(width / Math.max(10, total))));
      const gap = Math.max(2, Math.min(8, Math.floor(barWidth * 0.8)));
      const slice = state.sparkData.slice(-Math.floor(width / (barWidth + gap)) - 1);
      slice.forEach((v, i) => {
        const bar = document.createElement("div");
        bar.className = "spark-bar";
        bar.style.left = `${i * (barWidth + gap)}px`;
        bar.style.height = `${(Math.abs(v) / max) * 64}px`;
        bar.style.background = v > 0 ? "var(--pass)" : "var(--fail)";
        container.appendChild(bar);
      });
    }

function renderScenarios() {
  const failOnly = document.getElementById("failFilter").checked;
  const list = state.order
    .map(name => state.scenarios[name])
    .filter(Boolean)
    .filter(s => !failOnly || (s.status || "").toUpperCase() === "FAIL");

  document.getElementById("scenarioCountLabel").textContent = `${list.length} item${list.length === 1 ? "" : "s"}`;

  const grouped = list.reduce((acc, s) => {
    const feature = scenarioFeatures[s.name] || "Ungrouped";
    acc[feature] = acc[feature] || [];
    acc[feature].push(s);
    return acc;
  }, {});

  const html = Object.keys(grouped).sort().map(feature => {
    const cards = grouped[feature].map(s => {
      const displayName = stripGherkinPrefix(s.name);
      const status = (s.status || "RUNNING").toUpperCase();
      const statusClass = status === "FAIL" ? "badge-fail" : status === "PASS" ? "badge-pass" : "";
      const steps = s.open ? `<div class="scenario-steps">${formatSteps(s.steps)}</div>` : "";
      const videoButton = s.videoUrl
        ? `<div class="video-pill" onclick="event.stopPropagation(); window.open('${s.videoUrl}','_blank');">🎬 Watch</div>`
        : `<div class="video-pill" onclick="event.stopPropagation();">No video</div>`;
      const containerClass = status === "FAIL" ? "scenario scenario-fail" : status === "PASS" ? "scenario scenario-pass" : "scenario";
      return `
        <div class="${containerClass}" data-name="${s.name}" onclick="toggleScenario('${s.name.replace(/'/g, "\\'")}')">
          <div class="scenario-row">
            <div class="scenario-header">
              <div class="scenario-name">${displayName}</div>
            </div>
            <div class="scenario-meta inline-meta">
              <div class="${statusClass}">${status}</div>
              ${videoButton}
            </div>
            ${steps}
          </div>
        </div>`;
    }).join("");
    return `<div class="feature-block"><div class="feature-title">${feature}</div>${cards}</div>`;
  }).join("");
  document.getElementById("scenarios").innerHTML = html || "<div class='history-meta'>No scenarios yet.</div>";
}

window.toggleScenario = function(name) {
  const s = state.scenarios[name];
  if (!s) return;
  s.open = !s.open;
  renderScenarios();
};

function formatSteps(steps) {
  if (!steps || !steps.length) return "No steps captured";
  return steps.map(step => {
    const m = step.match(/^(Given|When|Then|And|But)\b\s*(.*)/i);
    if (m) {
      const rest = m[2] || "";
      return `<span class="step-line"><span class="step-text">* ${rest}</span></span>`;
    }
    return `<span class="step-line">${step}</span>`;
  }).join("");
}

    function renderCounts() {
      const { total, pass, fail } = currentCounts();
      document.getElementById("total").textContent = total;
      document.getElementById("passed").textContent = pass;
      document.getElementById("failed").textContent = fail;
      const running = Math.max(0, total - pass - fail);
      document.getElementById("running").textContent = running;
      renderDonut();
      renderSpark();
    }

    function renderAll() {
      computeCounts();
      renderCounts();
      renderScenarios();
    }

    // Reports state and rendering
    let reportsCache = [];
    let reportsPage = 0;
    const pageSize = 10;
    function loadAiSettings() {
      try {
        const raw = localStorage.getItem("aiSettings");
        if (!raw) return { provider: "gemini", apiKey: "" };
        return { provider: "gemini", apiKey: "", ...JSON.parse(raw) };
      } catch {
        return { provider: "gemini", apiKey: "" };
      }
    }
    function saveAiSettings(settings) {
      localStorage.setItem("aiSettings", JSON.stringify(settings));
    }
    function applyAiSettingsToForm() {
      const s = loadAiSettings();
      const prov = document.getElementById("aiProvider");
      const key = document.getElementById("aiApiKey");
      if (prov) prov.value = s.provider || "gemini";
      if (key) key.value = s.apiKey || "";
    }

    async function loadReports() {
      const historyEl = document.getElementById("reportsHistory");
      const summaryEl = document.getElementById("reportsSummary");
      if (!historyEl || !summaryEl) return;
      historyEl.textContent = "Loading...";
      try {
        const res = await fetch("/api/history");
        const data = await res.json();
        reportsCache = Array.isArray(data) ? data : [];
        reportsPage = 0;
        renderReportsSummary(reportsCache);
        renderReportsSpark(reportsCache);
        renderReportsPage();
      } catch (e) {
        historyEl.textContent = "Failed to load reports.";
      }
    }

    function renderReportsSummary(list) {
      const summaryEl = document.getElementById("reportsSummary");
      if (!summaryEl) return;
      const totalRuns = list.length;
      const totalPass = list.reduce((acc, r) => acc + (r.passedCount || 0), 0);
      const totalFail = list.reduce((acc, r) => acc + (r.failedCount || 0), 0);
      summaryEl.innerHTML = `
        <div class="stat"><div class="label">Runs</div><div class="value">${totalRuns}</div></div>
        <div class="stat"><div class="label">Passed</div><div class="value badge-pass">${totalPass}</div></div>
        <div class="stat"><div class="label">Failed</div><div class="value badge-fail">${totalFail}</div></div>
        <div class="stat"><div class="label">Select a run</div><div class="value">-</div></div>
      `;
    }

    function renderReportsSpark(list) {
      const container = document.getElementById("reportsSpark");
      if (!container) return;
      container.innerHTML = "";
      if (!list.length) return;
      const width = container.clientWidth || 300;
      const bars = Math.min(list.length, Math.max(10, Math.floor(width / 14)));
      const slice = list.slice(0, bars);
      const maxTotal = Math.max(...slice.map(r => (r.passedCount || 0) + (r.failedCount || 0)), 1);
      slice.forEach((run, idx) => {
        const total = (run.passedCount || 0) + (run.failedCount || 0);
        const passPct = total ? ((run.passedCount || 0) / total) * 100 : 0;
        const bar = document.createElement("div");
        bar.className = "spark-bar";
        bar.style.left = `${idx * 12}px`;
        bar.style.width = "8px";
        bar.style.height = `${(total / maxTotal) * 64}px`;
        bar.style.background = `linear-gradient(180deg, var(--pass) ${passPct}%, var(--fail) ${passPct}%)`;
        container.appendChild(bar);
      });
    }

    function renderReportsPage() {
      const historyEl = document.getElementById("reportsHistory");
      const pageInfo = document.getElementById("reportsPageInfo");
      if (!historyEl) return;
      const totalPages = Math.max(1, Math.ceil(reportsCache.length / pageSize));
      reportsPage = Math.min(Math.max(reportsPage, 0), totalPages - 1);
      const start = reportsPage * pageSize;
      const slice = reportsCache.slice(start, start + pageSize);
      if (pageInfo) pageInfo.textContent = `Page ${reportsPage + 1} / ${totalPages}`;
      if (!slice.length) {
        historyEl.textContent = "No runs yet.";
        return;
      }
      const html = slice.map(run => {
        const status = (run.failedCount || 0) > 0 ? "Fail" : "Pass";
        return `
          <div class="report-run" data-report="${run.id}">
            <div class="report-run-header" data-report="${run.id}">
              <div>
                <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                  <span class="report-run-name">Run #${run.id}</span>
                  <span class="badge ${status === "Pass" ? "badge-pass" : "badge-fail"}">${status}</span>
                </div>
                <div class="report-run-meta">
                  <span>${run.env || ""}</span>
                  <span>${run.browser || ""}</span>
                  <span>${run.startedAt || ""}</span>
                </div>
              </div>
              <div style="display:flex; gap:8px; flex-wrap:wrap;">
                <button class="ghost tiny-btn report-delete" data-id="${run.id}" type="button">Delete</button>
              </div>
            </div>
            <div class="report-detail" data-report-detail="${run.id}"></div>
          </div>
        `;
      }).join("");
      historyEl.innerHTML = html;
      historyEl.querySelectorAll(".report-run-header").forEach(el => {
        el.onclick = () => openReportRun(el.dataset.report);
      });
      historyEl.querySelectorAll(".report-delete").forEach(btn => {
        btn.onclick = (e) => {
          e.stopPropagation();
          const id = btn.dataset.id;
          openReportDeleteConfirm(id);
        };
      });
      // auto open first run in page
      const first = historyEl.querySelector(".report-run-header");
      if (first) first.click();
    }

    async function openReportRun(id) {
      const container = document.querySelector(`[data-report-detail="${id}"]`);
      if (!container) return;
      if (container.dataset.loaded === "true") {
        container.classList.toggle("open");
        container.style.display = container.style.display === "none" ? "block" : "none";
        return;
      }
      container.innerHTML = "Loading scenarios...";
      try {
        const runRes = await fetch(`/api/history/${id}`);
        const runData = await runRes.json();
        const res = await fetch(`/api/history/${id}/scenarios`);
        const scenariosRaw = await res.json();
        if (!Array.isArray(scenariosRaw) || !scenariosRaw.length) {
          container.innerHTML = "<div class='history-meta'>No scenarios.</div>";
          container.dataset.loaded = "true";
          return;
        }
        let scenarios = scenariosRaw.map(s => ({ ...s, status: normStatus(s.status) }));
        const passCount = scenarios.filter(s => isPassStatus(s.status)).length;
        const failCount = scenarios.filter(s => isFailStatus(s.status)).length;
        const total = Math.max(scenarios.length, 1);
        const passDeg = (passCount / total) * 360;
        const failDeg = ((passCount + failCount) / total) * 360;
        const percent = Math.round((passCount / Math.max(passCount + failCount, 1)) * 100);
        const summaryEl = document.getElementById("reportsSummary");
        if (summaryEl) {
          summaryEl.innerHTML = `
            <div class="stat">
              <div class="label">Run #${id}</div>
              <div class="value">${percent}% pass</div>
            </div>
            <div class="report-run-summary">
              <div class="mini-donut" style="background: conic-gradient(var(--pass) 0deg, var(--pass) ${passDeg}deg, var(--fail) ${passDeg}deg, var(--fail) ${failDeg}deg, rgba(255,255,255,0.08) ${failDeg}deg);">
                <div class="mini-donut-label">${percent}%</div>
              </div>
              <div class="report-run-counters">
                <span class="chip">Pass: ${passCount}</span>
                <span class="chip">Fail: ${failCount}</span>
                <span class="chip">Total: ${scenarios.length}</span>
              </div>
            </div>
          `;
        }
        const html = scenarios.map((s, idx) => {
          const steps = (s.stepsText || "").split("\n").filter(Boolean);
          const stepsHtml = steps.length ? `<ul>${steps.map(st => `<li>${st}</li>`).join("")}</ul>` : "<div>No steps</div>";
          const status = isFailStatus(s.status) ? "badge-fail" : "badge-pass";
          const videoPill = s.videoUrl
            ? `<span class="scenario-video-pill" onclick="event.stopPropagation(); window.open('${s.videoUrl}','_blank');">🎬 Watch</span>`
            : `<span class="scenario-video-pill disabled">No video</span>`;
          return `
            <div class="report-scenario" data-report-scenario="${id}-${idx}">
              <div class="report-scenario-header" data-toggle="${id}-${idx}">
                <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                  <span class="badge ${status}">${s.status || ""}</span>
                  <span class="report-scenario-name">${s.name}</span>
                  ${videoPill}
                </div>
              </div>
              <div class="report-steps" id="steps-${id}-${idx}">${stepsHtml}</div>
            </div>
          `;
        }).join("");
        container.innerHTML = html;
        container.dataset.loaded = "true";
        container.querySelectorAll("[data-toggle]").forEach(el => {
          el.onclick = () => {
            const targetId = el.dataset.toggle;
            const stepsEl = document.getElementById(`steps-${targetId}`);
            if (stepsEl) stepsEl.classList.toggle("open");
          };
        });
        // AI-style summary (heuristic)
        try {
          let logSnippet = "";
          if (runData && runData.logText) {
            const lines = (runData.logText || "").split("\n");
            const errorLines = lines.filter(l => /error|exception|failed/i.test(l)).slice(0, 3);
            if (errorLines.length) {
              logSnippet = errorLines.join(" | ");
            } else {
              logSnippet = (lines.slice(-5).join(" ") || "");
            }
          }
          const aiBox = document.createElement("div");
          aiBox.className = "ai-summary";
          const hasFail = failCount > 0;
          const s = loadAiSettings();
          const providerLabel = (s.provider || "gemini").toUpperCase();
          let message;
          if (hasFail) {
            message = `Overall: ${passCount} pass / ${failCount} fail. Key errors: ${logSnippet || "Not detected in log."}`;
          } else {
            message = `All ${passCount} scenarios passed. No issues detected.`;
          }
          aiBox.innerHTML = `
            <div class="ai-summary-title">AI Summary · ${providerLabel}</div>
            <div class="history-meta">${message}</div>
          `;
          container.prepend(aiBox);
        } catch (e) {
          // ignore
        }
      } catch (e) {
        container.innerHTML = "<div class='history-meta'>Failed to load scenarios.</div>";
      }
    }

    const navTabs = document.querySelectorAll(".nav-tab");
    const pages = document.querySelectorAll(".page-section");
    function switchPage(pageId) {
      const allowed = ["dashboard","reports","suites","settings"];
      const target = allowed.includes(pageId) ? pageId : "dashboard";
      pages.forEach(p => p.classList.toggle("active", p.id === `page-${target}`));
      navTabs.forEach(t => t.classList.toggle("active", t.dataset.page === target));
      localStorage.setItem("activePage", target);
      if (target === "reports") {
        loadReports();
      }
    }
    navTabs.forEach(tab => tab.addEventListener("click", () => switchPage(tab.dataset.page)));

    function appendLog(line) {
      const logEl = document.getElementById("log");
      logEl.textContent += line + "\n";
      logEl.scrollTop = logEl.scrollHeight;
    }

    function loadHistory() {
      fetch("/api/history")
        .then(r => r.json())
        .then(list => {
          if (!Array.isArray(list) || list.length === 0) {
            document.getElementById("history").textContent = "No runs yet.";
            return;
          }
          const html = list.slice(0, 12).map(run => {
            const status = run.failedCount > 0 ? "Fail" : "Pass";
            return `<div class="history-item">
              <div>
                <a href="#" data-id="${run.id}" class="open">Run #${run.id}</a>
                <div class="history-meta">${status} · ${run.passedCount || 0} pass / ${run.failedCount || 0} fail</div>
              </div>
              <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                <span class="chip">${run.env || ""} · ${run.browser || ""}</span>
                <button class="ghost open" data-id="${run.id}" type="button" style="padding:6px 10px;">Open</button>
                <button class="ghost delete" data-id="${run.id}" type="button" style="padding:6px 10px;">Delete</button>
              </div>
            </div>`;
          }).join("");
          const container = document.getElementById("history");
          container.innerHTML = html;
          container.querySelectorAll(".open").forEach(btn => {
            btn.onclick = (e) => {
              e.preventDefault();
              openRun(btn.dataset.id);
            };
          });
          container.querySelectorAll(".delete").forEach(btn => {
            btn.onclick = (e) => {
              e.preventDefault();
              deleteRun(btn.dataset.id);
            };
          });
        })
        .catch(() => {
          document.getElementById("history").textContent = "Failed to load history.";
        });
    }

function deleteRun(id) {
      return fetch(`/api/history/${id}`, { method: "DELETE" });
    }

    function openRun(id) {
      if (state.source) { state.source.close(); state.source = null; }
      resetUI(`Run #${id}`);
      state.runId = id;
      document.getElementById("currentRunLabel").textContent = `Run #${id}`;
      fetch(`/api/history/${id}/scenarios`)
        .then(r => r.json())
        .then(data => {
          state.scenarios = {};
          state.order = [];
          const seen = new Set();
          data.forEach(it => {
            const name = it.name;
            if (seen.has(name)) return;
            seen.add(name);
            state.order.push(name);
            state.scenarios[name] = {
              name,
              status: it.status || "PASS",
              steps: (it.stepsText || "").split("\n").filter(Boolean),
              videoUrl: it.videoUrl || null,
              open: false
            };
          });
          const pass = state.order.reduce((acc, n) => acc + ((state.scenarios[n]?.status || "").toUpperCase() === "PASS" ? 1 : 0), 0);
          const fail = state.order.reduce((acc, n) => acc + ((state.scenarios[n]?.status || "").toUpperCase() === "FAIL" ? 1 : 0), 0);
          const total = state.order.length;
          state.counts = { pass, fail };
          state.metaCounts = { total, pass, fail };
          renderAll();
        })
        .then(() => fetch(`/api/history/${id}`))
        .then(r => r.json())
        .then(run => {
          renderCounts();
          if (run && run.logText) document.getElementById("log").textContent = run.logText;
        })
        .catch(() => {});
    }

    function syncFromCapture(videoMode) {
      fetch("/api/run-capture?videoMode=" + videoMode)
        .then(r => r.json())
        .then(arr => {
          if (!arr || !arr.length) return;
          state.scenarios = {};
          state.order = [];
          arr.forEach(it => {
            ensureScenario(it.name);
            state.scenarios[it.name] = {
              name: it.name,
              status: it.status || "PASS",
              steps: (it.stepsText || "").split("\n").filter(Boolean),
              videoUrl: it.videoUrl || null,
              open: false
            };
          });
          renderAll();
        })
        .catch(() => {});
    }

    function toggleRunButton(disabled) {
      const btn = document.getElementById("runBtn");
      if (!btn) return;
      if (disabled) {
        btn.classList.add("disabled");
        btn.classList.add("running");
        btn.textContent = "Running...";
        btn.disabled = true;
      } else {
        btn.classList.remove("disabled");
        btn.classList.remove("running");
        btn.textContent = "Run";
        btn.disabled = false;
      }
    }

    function startRun() {
      if (state.source) return;
      resetUI("Starting...");
      toggleRunButton(true);
      state.metaCounts = null;
      buildCommandPreview();
      const params = new URLSearchParams({
        command: document.getElementById("command").value,
        env: document.getElementById("env").value,
        baseUrl: document.getElementById("baseUrl").value,
        browser: document.getElementById("browser").value,
        headless: document.getElementById("headless").value,
        parallelism: document.getElementById("parallelism").value,
        videoMode: document.getElementById("videoMode").value,
        workdir: document.getElementById("workingDir")?.value || "",
        suiteFilter: document.getElementById("suiteSelect")?.value || "",
        suiteName: document.getElementById("suiteSelect")?.selectedOptions?.[0]?.textContent?.trim() || "",
        suiteTag: ""
      });
      state.source = new EventSource("/api/run?" + params.toString());
      document.getElementById("status").textContent = "Running...";
      document.getElementById("runMeta").textContent = "Execution in progress";
      state.sparkData = [];

      state.source.addEventListener("log", e => appendLog(e.data));
      state.source.addEventListener("run-id", e => {
        state.runId = e.data;
        document.getElementById("status").textContent = `Run #${state.runId}`;
        document.getElementById("currentRunLabel").textContent = `Run #${state.runId}`;
      });
      state.source.addEventListener("scenario", e => {
        const name = e.data.trim();
        ensureScenario(name);
        renderAll();
      });
      state.source.addEventListener("scenario-step", e => {
        const [rawName, step] = e.data.split("::");
        const name = (rawName || "").trim();
        ensureScenario(name);
        state.scenarios[name].steps.push((step || "").trim());
        renderScenarios();
      });
      state.source.addEventListener("scenario-pass", e => {
        const name = e.data.trim();
        ensureScenario(name);
        const s = state.scenarios[name];
        if (s.status === "FAIL" && state.counts.fail > 0) state.counts.fail--;
        s.status = "PASS";
        state.counts.pass++;
        state.sparkData.push(1);
        renderAll();
      });
      state.source.addEventListener("scenario-fail", e => {
        const [rawName, videoUrl] = e.data.split("::");
        const name = (rawName || "").trim();
        ensureScenario(name);
        const s = state.scenarios[name];
        if (s.status === "PASS" && state.counts.pass > 0) state.counts.pass--;
        if (s.status !== "FAIL") state.counts.fail++;
        s.status = "FAIL";
        if (videoUrl) s.videoUrl = videoUrl.trim();
        state.sparkData.push(-1);
        renderAll();
      });
      state.source.addEventListener("sync", e => {
        try {
          const arr = JSON.parse(e.data || "[]");
          state.scenarios = {};
          state.order = [];
          arr.forEach(it => {
            ensureScenario(it.name);
            state.scenarios[it.name] = {
              name: it.name,
              status: it.status || "PASS",
              steps: (it.stepsText || "").split("\n").filter(Boolean),
              videoUrl: it.videoUrl || null,
              open: false
            };
          });
          renderAll();
        } catch (err) {
          console.error(err);
        }
      });
      const closeSource = () => {
        if (state.source) { state.source.close(); state.source = null; }
        toggleRunButton(false);
      };
      state.source.addEventListener("complete", () => {
        document.getElementById("status").textContent = "Completed";
        document.getElementById("runMeta").textContent = "Finished";
        closeSource();
        loadHistory();
        syncFromCapture(document.getElementById("videoMode").value);
      });
      state.source.addEventListener("error", () => {
        document.getElementById("status").textContent = "Connection lost";
        closeSource();
      });
    }

    const runBtnEl = document.getElementById("runBtn");
    if (runBtnEl) runBtnEl.onclick = startRun;
    const resetBtnEl = document.getElementById("resetBtn");
    if (resetBtnEl) resetBtnEl.onclick = () => { if (state.source) { state.source.close(); state.source = null; } resetUI("Idle"); toggleRunButton(false); };
    const resetDbBtnEl = document.getElementById("resetDbBtn");
    if (resetDbBtnEl) resetDbBtnEl.onclick = () => {
      const backdrop = document.createElement("div");
      backdrop.className = "modal-backdrop";
      backdrop.innerHTML = `
        <div class="modal">
          <h3>Confirm reset</h3>
          <p>Type <b>DELETE</b> to wipe all runs and scenario stats.</p>
          <input id="confirmInput" />
          <div class="modal-actions">
            <button id="confirmCancel" class="ghost" type="button">Cancel</button>
            <button id="confirmOk" class="primary" type="button">Reset</button>
          </div>
        </div>`;
      document.body.appendChild(backdrop);
      const cleanup = () => backdrop.remove();
      backdrop.querySelector("#confirmCancel").onclick = cleanup;
      backdrop.querySelector("#confirmOk").onclick = async () => {
        const val = backdrop.querySelector("#confirmInput").value;
        if (val !== "DELETE") { cleanup(); return; }
        try {
          await fetch("/api/admin/reset", { method: "POST" });
          resetUI("DB reset");
          loadHistory();
        } catch (e) {
          alert("Reset failed");
        } finally {
          cleanup();
        }
      };
    };
    document.getElementById("clearLogBtn").onclick = () => document.getElementById("log").textContent = "";
    document.getElementById("failFilter").onchange = renderScenarios;
    ["command","env","baseUrl","browser","headless","parallelism","videoMode"].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.addEventListener("change", buildCommandPreview);
      if (el) el.addEventListener("input", buildCommandPreview);
    });
    const previewToggle = document.getElementById("cmdPreviewToggle");
    if (previewToggle) {
      previewToggle.addEventListener("click", toggleCommandPreview);
    }
    const reportsPrev = document.getElementById("reportsPrev");
    const reportsNext = document.getElementById("reportsNext");
    const refreshReports = document.getElementById("refreshReports");
    if (reportsPrev) reportsPrev.onclick = () => { reportsPage = Math.max(0, reportsPage - 1); renderReportsPage(); };
    if (reportsNext) reportsNext.onclick = () => { reportsPage = reportsPage + 1; renderReportsPage(); };
    if (refreshReports) refreshReports.onclick = loadReports;
    const aiSaveBtn = document.getElementById("aiSaveBtn");
    if (aiSaveBtn) aiSaveBtn.onclick = () => {
      const prov = document.getElementById("aiProvider")?.value || "gemini";
      const key = document.getElementById("aiApiKey")?.value || "";
      saveAiSettings({ provider: prov, apiKey: key });
      alert("AI settings saved locally.");
    };

    function openReportDeleteConfirm(id) {
      const backdrop = document.createElement("div");
      backdrop.className = "modal-backdrop";
      backdrop.innerHTML = `
        <div class="modal">
          <h3>Delete Run #${id}?</h3>
          <p>This will remove the run, its scenarios and steps.</p>
          <div class="modal-actions">
            <button class="ghost" id="reportDelCancel">Cancel</button>
            <button class="primary" id="reportDelOk">Delete</button>
          </div>
        </div>
      `;
      document.body.appendChild(backdrop);
      const cleanup = () => backdrop.remove();
      backdrop.querySelector("#reportDelCancel").onclick = cleanup;
      backdrop.querySelector("#reportDelOk").onclick = async () => {
        try {
          await deleteRun(id);
          await loadReports();
          await loadHistory();
        } catch (e) {
          // ignore
        } finally {
          cleanup();
        }
      };
      backdrop.onclick = (e) => { if (e.target === backdrop) cleanup(); };
    }

    window.addEventListener("load", () => {
      resetUI("Idle");
      loadHistory();
      buildCommandPreview();
      const savedPage = localStorage.getItem("activePage") || "dashboard";
      switchPage(savedPage);
      updateClock();
      setInterval(updateClock, 1000);
      const body = document.getElementById("cmdPreviewBody");
      const arrow = document.getElementById("cmdPreviewArrow");
      if (body) body.style.display = previewOpen ? "flex" : "none";
      if (arrow) arrow.style.transform = previewOpen ? "rotate(0deg)" : "rotate(-90deg)";
      loadDefaults(true);
      fetchScenarioNames();
      fetchSuites();
      applyAiSettingsToForm();
    });

const defaultsLoadBtn=document.getElementById('defaultsLoadBtn');
const defaultsSaveBtn=document.getElementById('defaultsSaveBtn');
function applyDefaultsToForm(s){ if(!s)return; if(s.command) document.getElementById('command').value=s.command; if(s.env) document.getElementById('env').value=s.env; if(s.baseUrl) document.getElementById('baseUrl').value=s.baseUrl; if(s.browser) document.getElementById('browser').value=s.browser; if(s.parallelism) document.getElementById('parallelism').value=s.parallelism; if(s.headless) document.getElementById('headless').value=s.headless; if(s.videoMode) document.getElementById('videoMode').value=s.videoMode; if(s.workingDir) document.getElementById('workingDir').value=s.workingDir; buildCommandPreview(); }
function applyDefaultsToModal(s){ if(!s)return; const m=[['defCommand','command'],['defEnv','env'],['defBaseUrl','baseUrl'],['defBrowser','browser'],['defParallelism','parallelism'],['defHeadless','headless'],['defVideoMode','videoMode'],['defWorkingDir','workingDir']]; m.forEach(([id,key])=>{ if(s[key]!==undefined && document.getElementById(id)) document.getElementById(id).value=s[key]; }); }
async function loadDefaults(applyForm=false){ const wd=(document.getElementById('workingDir')?.value||document.getElementById('defWorkingDir')?.value||'').trim(); try{ const res=await fetch(`/api/run-settings?workdir=${encodeURIComponent(wd)}`); const data=await res.json(); if(data && data.success && data.settings){ if(applyForm) applyDefaultsToForm(data.settings); applyDefaultsToModal(data.settings); } } catch(e){} }
async function saveDefaults(){ const payload={ workdir:(document.getElementById('defWorkingDir')?.value||document.getElementById('workingDir')?.value||'').trim(), command:document.getElementById('defCommand').value, env:document.getElementById('defEnv').value, baseUrl:document.getElementById('defBaseUrl').value, browser:document.getElementById('defBrowser').value, parallelism:document.getElementById('defParallelism').value, headless:document.getElementById('defHeadless').value, videoMode:document.getElementById('defVideoMode').value, workingDir:document.getElementById('defWorkingDir').value }; try{ const res=await fetch('/api/run-settings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}); const data=await res.json(); if(data && data.success){ applyDefaultsToForm(payload); } }catch(e){} }
if(defaultsLoadBtn) defaultsLoadBtn.onclick=()=>loadDefaults(true);
if(defaultsSaveBtn) defaultsSaveBtn.onclick=saveDefaults;

const suiteSave=document.getElementById('suiteSave');
const suiteDelete=document.getElementById('suiteDelete');
const suiteNameInput=document.getElementById('suiteName');
const suiteSelect=document.getElementById('suiteSelect');
const scenarioList=document.getElementById('scenarioList');
let scenarioNames=[];
let suites=[];
let scenarioFeatures = {};
// schedule controls
const scheduleSuiteSelect = document.getElementById('scheduleSuiteSelect');
const scheduleCronInput = document.getElementById('scheduleCron');
const scheduleNotifyInput = document.getElementById('scheduleNotify');
const planCommand = document.getElementById('planCommand');
const planEnv = document.getElementById('planEnv');
const planBaseUrl = document.getElementById('planBaseUrl');
const planBrowser = document.getElementById('planBrowser');
const planHeadless = document.getElementById('planHeadless');
const planParallelism = document.getElementById('planParallelism');
const planVideoMode = document.getElementById('planVideoMode');
const planWorkingDir = document.getElementById('planWorkingDir');

async function fetchScenarioNames(){
  try{
    const res=await fetch('/api/suites/scenarios');
    const data=await res.json();
    scenarioNames=[];
    scenarioFeatures={};
    (Array.isArray(data)?data:[]).forEach(entry=>{
      if(typeof entry === 'string'){
        scenarioNames.push(entry);
      }else if(entry && entry.name){
        scenarioNames.push(entry.name);
        if(entry.feature) scenarioFeatures[entry.name]=entry.feature;
      }
    });
    renderScenarioList();
  }catch(e){ if(scenarioList) scenarioList.innerHTML='Failed to load scenarios'; }
}

async function fetchSuites(){
  try{
    const res=await fetch('/api/suites');
    const data=await res.json();
    suites=Array.isArray(data)?data:[];
    renderSuites();
  }catch(e){}
}

function renderSuites(){
  if(suiteSelect){
    suiteSelect.innerHTML='<option value=\"\">-- no suite --</option>';
    suites.forEach(s=>{
      const o=document.createElement('option');
      o.value=(s.scenarios||[]).map(n=>n.replace(/\"/g,'\\\\\"')).join('|');
      const count = (s.scenarios || []).length;
      o.textContent = count ? `${s.name} [${count}]` : s.name;
      const tag = s.tag || autoTagFromName(s.name);
      o.dataset.tag = tag;
      suiteSelect.appendChild(o);
    });
    suiteSelect.onchange = handleSuiteSelection;
  }
  if(scheduleSuiteSelect){
    scheduleSuiteSelect.innerHTML='<option value=\"\">-- select suite --</option>';
    suites.forEach(s=>{
      const o=document.createElement('option');
      o.value=s.name;
      o.textContent=s.name;
      o.dataset.schedule = s.schedule || "";
      o.dataset.notify = s.notify ? "true" : "false";
      scheduleSuiteSelect.appendChild(o);
    });
    scheduleSuiteSelect.onchange = () => {
      const opt = scheduleSuiteSelect.selectedOptions?.[0];
      if (!opt || !opt.value) {
        if (planCommand) {
          planCommand.disabled = false;
          planCommand.classList.remove('disabled-input');
          planCommand.value = planCommand.value || "mvn test";
        }
        return;
      }
      if (scheduleCronInput) scheduleCronInput.value = opt.dataset.schedule || "";
      if (scheduleNotifyInput) scheduleNotifyInput.checked = opt.dataset.notify === "true";
      const suiteObj = suites.find(x => x.name === opt.value) || {};
      if (planCommand) {
        planCommand.value = "mvn test";
        planCommand.disabled = true;
        planCommand.classList.add('disabled-input');
      }
      if (planEnv) planEnv.value = suiteObj.env || "local";
      if (planBaseUrl) planBaseUrl.value = suiteObj.baseUrl || "https://playwright.dev";
      if (planBrowser) planBrowser.value = suiteObj.browser || "chromium";
      if (planHeadless) planHeadless.value = String(suiteObj.headless ?? "true");
      if (planParallelism) planParallelism.value = suiteObj.parallelism || "4";
      if (planVideoMode) planVideoMode.value = suiteObj.videoMode || "fail";
      if (planWorkingDir) planWorkingDir.value = suiteObj.workingDir || "";
    };
  }
  renderPlanList();
  renderSuitePane();
}

function loadPlanIntoForm(name){
  const suite = suites.find(s => s.name === name);
  if (!suite) return;
  if (scheduleSuiteSelect) scheduleSuiteSelect.value = suite.name;
  if (scheduleCronInput) scheduleCronInput.value = suite.schedule || "";
  if (scheduleNotifyInput) scheduleNotifyInput.checked = !!suite.notify;
  if (planCommand) planCommand.value = suite.command || "mvn test";
  if (planEnv) planEnv.value = suite.env || "local";
  if (planBaseUrl) planBaseUrl.value = suite.baseUrl || "https://playwright.dev";
  if (planBrowser) planBrowser.value = suite.browser || "chromium";
  if (planHeadless) planHeadless.value = String(suite.headless ?? "true");
  if (planParallelism) planParallelism.value = suite.parallelism || "4";
  if (planVideoMode) planVideoMode.value = suite.videoMode || "fail";
  if (planWorkingDir) planWorkingDir.value = suite.workingDir || "";
}

function openSuitesModal(prefillName){
  switchPage('suites');
  const targetName = prefillName || suiteSelect?.selectedOptions?.[0]?.textContent?.trim() || '';
  if (targetName) {
    loadSuiteIntoForm(targetName);
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}
function renderScenarioList(){
  if(!scenarioList) return;
  if(!scenarioNames.length){ scenarioList.innerHTML='No scenarios found.'; return; }
  const grouped = scenarioNames.reduce((acc, n)=>{
    const f = scenarioFeatures[n] || 'Ungrouped';
    acc[f] = acc[f] || [];
    acc[f].push(n);
    return acc;
  },{});
  const html = Object.keys(grouped).sort().map(f=>{
    const items = grouped[f].map(n=>`
      <label class="suite-item">
        <input type="checkbox" class="suite-scenario" value="${n}">
        <span class="suite-name">${n}</span>
        <span class="suite-cuke" aria-hidden="true">🥒</span>
      </label>`).join('');
    return `<div class="suite-group"><div class="suite-group-title">${f}</div>${items}</div>`;
  }).join('');
  scenarioList.innerHTML = html;
}

function loadSuiteIntoForm(name){
  const suite=suites.find(s=> s.name===name);
  document.querySelectorAll('.suite-scenario').forEach(cb=> cb.checked = false);
  if(!suite) return;
  suiteNameInput.value=suite.name;
  document.querySelectorAll('.suite-scenario').forEach(cb=> {
    cb.checked = suite.scenarios && suite.scenarios.includes(cb.value);
  });
}

function setAllSuiteCheckboxes(flag){
  document.querySelectorAll('.suite-scenario').forEach(cb=> cb.checked = flag);
}

const suiteSelectAllBtn = document.getElementById('suiteSelectAll');
const suiteClearAllBtn = document.getElementById('suiteClearAll');
if(suiteSelectAllBtn) suiteSelectAllBtn.onclick = ()=> setAllSuiteCheckboxes(true);
if(suiteClearAllBtn) suiteClearAllBtn.onclick = ()=> {
  setAllSuiteCheckboxes(false);
  if (suiteNameInput) suiteNameInput.value = '';
};

// delegate suite edit buttons (Saved Suites card)
document.addEventListener('click', async (e) => {
  const btn = e.target.closest('.suite-pane-edit');
  if (btn) {
    e.preventDefault();
    try {
      await fetchSuites();
    } catch(err) {}
    openSuitesModal(btn.dataset.suite);
  }
});

function renderPlanList(){
  const listEl = document.getElementById('scheduleList');
  if (!listEl) return;
  const planned = suites.filter(s => s.schedule && s.schedule.trim().length);
  if (!planned.length) {
    listEl.innerHTML = "<div class='history-meta'>No scheduled plans.</div>";
    return;
  }
  listEl.innerHTML = planned.map(s => {
    const notify = s.notify ? "Mail: yes" : "Mail: no";
    return `
      <div class="plan-item">
        <div>
          <div class="plan-title">${s.name}</div>
          <div class="plan-meta">
            <span>${s.schedule}</span>
            <span>${notify}</span>
            <span>${s.command || "mvn test"}</span>
          </div>
        </div>
        <div class="plan-actions">
          <button type="button" class="ghost tiny-btn plan-edit" data-plan="${s.name}">Edit</button>
          <button type="button" class="ghost tiny-btn plan-delete" data-plan="${s.name}">Delete</button>
        </div>
      </div>
    `;
  }).join("");
  listEl.querySelectorAll('.plan-edit').forEach(btn => {
    btn.onclick = () => loadPlanIntoForm(btn.dataset.plan);
  });
  listEl.querySelectorAll('.plan-delete').forEach(btn => {
    btn.onclick = () => openPlanDeleteConfirm(btn.dataset.plan);
  });
}

function renderSuitePane(){
  const pane = document.getElementById('suiteListPane');
  if (!pane) return;
  if (!suites.length) {
    pane.innerHTML = "<div class='history-meta'>No suites yet.</div>";
    return;
  }
  pane.innerHTML = suites.map(s => {
    const count = (s.scenarios || []).length;
    return `<div class="history-item" style="padding:6px 8px; gap:6px;">
      <div>
        <div class="plan-title">${s.name}</div>
        <div class="history-meta">${count} scenario${count===1?"":"s"}</div>
      </div>
      <div style="display:flex; gap:6px; align-items:center;">
        <button class="ghost tiny-btn suite-pane-edit" data-suite="${s.name}">Edit</button>
      </div>
    </div>`;
  }).join("");
  pane.querySelectorAll('.suite-pane-edit').forEach(btn => {
    btn.onclick = () => openSuitesModal(btn.dataset.suite);
  });
}
const scheduleSaveBtn = document.getElementById('scheduleSave');
if (scheduleSaveBtn) {
  scheduleSaveBtn.onclick = async () => {
    const suiteName = scheduleSuiteSelect?.value || "";
    const cron = scheduleCronInput?.value?.trim() || "";
    const notify = scheduleNotifyInput?.checked || false;
    const payload = {
      name: suiteName,
      schedule: cron,
      notify,
      command: planCommand?.value || "mvn test",
      env: planEnv?.value || "local",
      baseUrl: planBaseUrl?.value || "https://playwright.dev",
      browser: planBrowser?.value || "chromium",
      headless: planHeadless?.value || "true",
      parallelism: planParallelism?.value || "4",
      videoMode: planVideoMode?.value || "fail",
      workingDir: planWorkingDir?.value || ""
    };
    if (!suiteName) {
      alert("Select a suite to schedule.");
      return;
    }
    await fetch('/api/suites/schedule', {
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body: JSON.stringify(payload)
    });
    await fetchSuites();
    renderSuites();
    alert("Schedule saved.");
  };
}

function openPlanDeleteConfirm(name){
  if(!name) return;
  const backdrop = document.createElement("div");
  backdrop.className = "modal-backdrop";
  backdrop.innerHTML = `
    <div class="modal">
      <h3>Delete schedule for ${name}?</h3>
      <p>This removes the plan and stops future runs.</p>
      <div class="modal-actions">
        <button class="ghost" id="planDelCancel">Cancel</button>
        <button class="primary" id="planDelOk">Delete</button>
      </div>
    </div>
  `;
  document.body.appendChild(backdrop);
  const cleanup = () => backdrop.remove();
  backdrop.querySelector("#planDelCancel").onclick = cleanup;
  backdrop.querySelector("#planDelOk").onclick = async () => {
    try{
      await fetch(`/api/suites/schedule?name=${encodeURIComponent(name)}`, {method:'DELETE'});
      await fetchSuites();
      renderSuites();
    }catch(e){}
    cleanup();
  };
  backdrop.onclick = (e)=>{ if(e.target===backdrop) cleanup(); };
}

if(suiteSave){
  suiteSave.onclick=async ()=>{
    const name=suiteNameInput.value.trim();
    const selected=[...document.querySelectorAll('.suite-scenario')].filter(cb=>cb.checked).map(cb=>cb.value);
    if(!name || !selected.length) return;
    await fetch('/api/suites', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({name, tag: "", scenarios: selected})});
    await fetchSuites();
    renderSuites();
    renderSuitePane();
    if (suiteSelect) {
      [...suiteSelect.options].forEach(opt => {
        if (opt.textContent.trim() === name) opt.selected = true;
      });
    }
    handleSuiteSelection();
  };
}

if(suiteDelete){
  suiteDelete.onclick=async ()=>{
    const name=suiteNameInput.value.trim();
    if(!name) return;
    await fetch(`/api/suites?name=${encodeURIComponent(name)}`, {method:'DELETE'});
    await fetchSuites();
    renderSuites();
    renderSuitePane();
    suiteNameInput.value='';
    if(suiteSelect) suiteSelect.value='';
    buildCommandPreview();
  };
}

function stripGherkinPrefix(name){
  if(!name) return '';
  return name.replace(/^(Given|When|Then|And|But)\b\s*/i,'').trim();
}

window.toggleScenarioSelect = function(name, checked){
  const s = state.scenarios[name];
  if (!s) return;
  s.selected = checked;
  renderScenarios();
};

    function handleSuiteSelection(){
  const select = document.getElementById('suiteSelect');
  const commandInput = document.getElementById('command');
  const chosen = select?.value;
  const tag = select?.selectedOptions?.[0]?.dataset?.tag || '';
  if(!commandInput) return;
  if (chosen !== undefined && chosen !== null && chosen.trim().length > 0) {
    commandInput.value = 'mvn test';
    commandInput.disabled = true;
    commandInput.classList.add('disabled-input');
  } else {
    commandInput.disabled = false;
    commandInput.classList.remove('disabled-input');
  }
  buildCommandPreview();
}
