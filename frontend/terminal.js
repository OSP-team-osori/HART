/**
 * ============================================================================
 * [HART TADD] 가상 터미널 실시간 스트리밍 제어 및 백엔드 POST 연동
 * ============================================================================
 */

const CONFIG = {
    // ✅ 실제 백엔드와 통신하기 위해 false로 유지
    USE_MOCK: true,
    // ✅ 백엔드 SSE 스트리밍 엔드포인트 주소 고정 (8090 포트)
    API_URL: '/api/v1/agent/stream'
};

// 1. DOM 요소 선택
const terminalContainer = document.getElementById('terminal-content');

// 기존 하드코딩된 더미 텍스트를 비우고 시작
if (terminalContainer) {
    terminalContainer.innerHTML = '';
}

let currentLineElement = null;

// 2. UI 렌더링 함수
function renderLog(chunk) {
    if (!terminalContainer) return;

    const parts = chunk.split('\n');

    parts.forEach((part, index) => {
        if (!currentLineElement) {
            currentLineElement = document.createElement('div');
            currentLineElement.className = "text-gray-300";
            currentLineElement.style.whiteSpace = "pre-wrap";
            terminalContainer.appendChild(currentLineElement);
        }

        if (part !== '') {
            currentLineElement.appendChild(document.createTextNode(part));

            const text = currentLineElement.textContent;
            if (text.includes("[INFO]")) {
                currentLineElement.className = "text-primary font-semibold whitespace-pre-wrap";
            } else if (text.includes("[WARN]")) {
                currentLineElement.className = "text-yellow-400 font-semibold whitespace-pre-wrap";
            } else if (text.includes("[SUCCESS]")) {
                currentLineElement.className = "text-primary font-bold whitespace-pre-wrap";
            } else if (text.includes("[ERROR]") || text.includes("FAIL")) {
                currentLineElement.className = "text-danger font-bold whitespace-pre-wrap";
            }
        }

        if (index < parts.length - 1) {
            currentLineElement = null;
        }
    });

    currentLineElement = null;
    terminalContainer.scrollTop = terminalContainer.scrollHeight;
}

// 3. 실제 백엔드 연동 로직 (SSE 스트리밍)
function connectRealBackend() {
    console.log("[Terminal] 백엔드 실시간 연결 시도 중...");

    const eventSource = new EventSource(CONFIG.API_URL);

    eventSource.addEventListener("log", (event) => {
        renderLog(event.data);
        // 결과 JSON이 감지되면 stat 카드를 실제 값으로 갱신한다
        detectAndApplyResult(event.data);
    });

    eventSource.onerror = () => {
        eventSource.close();
        renderLog("\n[WARN] 서버 연결이 끊어졌습니다. 3초 후 재연결 시도...\n");
        setTimeout(connectRealBackend, 3000);
    };
}

// 4. UI 테스트용 가짜 로직 (현재는 사용 안 함)
async function runMockTerminal() {
    console.log("[Terminal] 더미 데이터 스트리밍 시작...");
    try {
        const response = await fetch('../mock/dummy_stream.json');
        if (!response.ok) throw new Error("JSON 파일을 찾을 수 없습니다.");

        const originalData = await response.json();
        const dummyStreamData = [];
        for (let i = 0; i < 5; i++) {
            dummyStreamData.push(...originalData);
        }

        let currentIndex = 0;

        function processNextChunk() {
            if (currentIndex >= dummyStreamData.length) {
                const cursor = document.createElement('span');
                cursor.className = "blinking-cursor bg-white w-2 h-4 inline-block align-middle ml-2";
                if (currentLineElement) {
                    currentLineElement.appendChild(cursor);
                } else {
                    const cursorWrap = document.createElement('div');
                    cursorWrap.appendChild(cursor);
                    terminalContainer.appendChild(cursorWrap);
                }
                terminalContainer.scrollTop = terminalContainer.scrollHeight;
                return;
            }

            const data = dummyStreamData[currentIndex];
            renderLog(data.chunk);
            currentIndex++;
            setTimeout(processNextChunk, (data.delay * 1000));
        }

        processNextChunk();
    } catch (error) {
        console.error("더미 데이터를 불러오는 중 에러 발생:", error);
        renderLog("[ERROR] 더미 데이터를 불러올 수 없습니다.\n", "error");
    }
}

// ============================================================================
// 5. 실행 결과 감지 및 Stats 카드 업데이트 모듈
// SSE 스트림의 마지막 라인이 결과 JSON이면 파싱하여 stat 카드에 반영한다.
// 백엔드 ProcessOrchestratorService.broadcastLog()가 전송하는 JSON을 활용한다.
// 결과 JSON 필드: test_status, total_tokens, cost, test_summary
// ============================================================================

// 프론트엔드 기준 실행 시작 시각 (Run Agent 클릭 시 기록)
let executionStartTime = null;

/**
 * SSE log 데이터에서 결과 JSON을 감지하고, stat 카드에 실제 값을 반영한다.
 * JSON이 아니거나 파싱에 실패하면 아무 동작도 하지 않는다.
 */
function detectAndApplyResult(data) {
    const result = tryParseResultJson(data);
    if (!result) return;

    const elapsedMs = executionStartTime ? Date.now() - executionStartTime : null;
    updateStatCards(result, elapsedMs);
    executionStartTime = null;

    // 스트림 종료 후 DB 저장 시간을 고려하여 1초 뒤에 최신 지표 갱신
    setTimeout(fetchLatestResult, 1000);
}

/**
 * 문자열이 결과 JSON인지 판별하고 파싱을 시도한다.
 * 결과 JSON에는 반드시 test_status 필드가 포함되어야 한다.
 * @param {string} data - SSE log 이벤트의 data 값
 * @returns {Object|null} 결과 객체 또는 null
 */
function tryParseResultJson(data) {
    const trimmed = data.trim();
    if (!trimmed.startsWith('{')) return null;

    try {
        const parsed = JSON.parse(trimmed);
        if (parsed.test_status) return parsed;
        return null;
    } catch (e) {
        console.warn('[Stats] JSON 파싱 실패:', e.message);
        return null;
    }
}

/**
 * stat 카드 DOM 요소를 실제 결과 값으로 일괄 갱신한다.
 * @param {Object} result - 결과 JSON (test_status, total_tokens, cost 포함)
 * @param {number|null} elapsedMs - 실행 소요 시간(ms), 측정 불가 시 null
 */
function updateStatCards(result, elapsedMs) {
    updateTestResult(result.testStatus || result.test_status);
    updateExecutionTime(result.executionTimeMs != null ? result.executionTimeMs : elapsedMs);
    updateTokenCost(result.tokensUsed || result.total_tokens);
    if (result.confidenceScore !== undefined) {
        updateReliabilityScore(result.confidenceScore);
    }
}

/**
 * Reliability Score 카드를 갱신한다.
 */
function updateReliabilityScore(score) {
    const el = document.getElementById('stat-reliability');
    if (!el) return;

    if (score == null) {
        el.textContent = '--';
        return;
    }
    el.textContent = score;
}

/**
 * 백엔드에서 최신 실행 결과를 가져와 UI에 초기 세팅한다.
 */
async function fetchLatestResult() {
    if (CONFIG.USE_MOCK) {
        // 백엔드 연결 없이 프론트엔드 UI만 테스트하기 위한 가짜 데이터 반환
        const mockData = {
            testStatus: "PASS",
            executionTimeMs: 4200,
            tokensUsed: 1542,
            confidenceScore: 92
        };
        updateStatCards(mockData, null);
        return;
    }

    try {
        const res = await fetch('/api/v1/agent/latest-result');
        if (!res.ok) return;
        const data = await res.json();
        updateStatCards(data, null);
    } catch (e) {
        console.warn('[Stats] 최신 결과 불러오기 실패:', e.message);
    }
}

/**
 * Test Result 카드를 갱신한다.
 * PASS/SKIPPED이면 녹색(secondary), FAIL이면 빨간색(danger)으로 표시한다.
 */
function updateTestResult(testStatus) {
    const el = document.getElementById('stat-test-result');
    if (!el) return;

    const status = testStatus.toUpperCase();
    const isPass = (status === 'PASS' || status === 'SKIPPED');

    el.textContent = status;
    el.className = isPass
        ? 'text-[34px] font-bold text-primary'
        : 'text-[34px] font-bold text-danger';
}

/**
 * Execution Time 카드를 갱신한다.
 * 밀리초를 초 단위(소수점 1자리)로 변환하여 표시한다.
 */
function updateExecutionTime(elapsedMs) {
    const el = document.getElementById('stat-execution-time');
    if (!el) return;

    if (elapsedMs == null) {
        el.innerHTML = '--<span class="text-[22px] text-gray-500">s</span>';
        return;
    }

    const seconds = (elapsedMs / 1000).toFixed(1);
    el.innerHTML = seconds + '<span class="text-[22px] text-gray-500">s</span>';
}

/**
 * Token Cost 카드를 갱신한다.
 * 숫자를 천 단위 콤마 포맷으로 변환하여 표시한다.
 */
function updateTokenCost(totalTokens) {
    const el = document.getElementById('stat-token-cost');
    if (!el) return;

    if (totalTokens == null) {
        el.textContent = '--';
        return;
    }

    el.textContent = Number(totalTokens).toLocaleString();
}

// ============================================================================
// 6. GitHub 토큰 연동 UI 모듈
// ============================================================================
function initGithubTokenUI() {
    const connectBtn = document.getElementById('github-connect-btn');
    const tokenPanel = document.getElementById('github-token-panel');
    const tokenInput = document.getElementById('github-token-input');
    const saveBtn = document.getElementById('github-token-save-btn');
    const clearBtn = document.getElementById('github-token-clear-btn');
    const statusDot = document.getElementById('github-status-dot');
    const btnLabel = document.getElementById('github-btn-label');

    if (!connectBtn) return;

    function applyTokenState(token) {
        if (token) {
            statusDot.classList.remove('hidden');
            btnLabel.textContent = 'GitHub 연동됨';
            connectBtn.classList.add('text-secondary', 'border-secondary/30');
            connectBtn.classList.remove('text-gray-400');
        } else {
            statusDot.classList.add('hidden');
            btnLabel.textContent = 'GitHub 연동';
            connectBtn.classList.remove('text-secondary', 'border-secondary/30');
            connectBtn.classList.add('text-gray-400');
        }
    }

    // 저장된 토큰 불러오기
    applyTokenState(localStorage.getItem('github_token'));

    connectBtn.addEventListener('click', () => {
        tokenPanel.classList.toggle('hidden');
        if (!tokenPanel.classList.contains('hidden')) {
            const saved = localStorage.getItem('github_token');
            if (saved) tokenInput.value = saved;
            tokenInput.focus();
        }
    });

    saveBtn.addEventListener('click', () => {
        const token = tokenInput.value.trim();
        if (!token) { alert('토큰을 입력해주세요.'); return; }
        localStorage.setItem('github_token', token);
        tokenPanel.classList.add('hidden');
        tokenInput.value = '';
        applyTokenState(token);
    });

    clearBtn.addEventListener('click', () => {
        localStorage.removeItem('github_token');
        tokenInput.value = '';
        tokenPanel.classList.add('hidden');
        applyTokenState(null);
    });
}

// 5. 엔트리 포인트 및 POST 버튼 로직
document.addEventListener("DOMContentLoaded", () => {
    if (CONFIG.USE_MOCK) {
        runMockTerminal();
        fetchLatestResult(); // Mock 환경에서도 초기 데이터 렌더링
    } else {
        connectRealBackend();
        fetchLatestResult(); // 페이지 초기 진입 시 최신 지표 불러오기
    }

    initGithubTokenUI();

    // 버튼 클릭 시 프롬프트를 백엔드로 전송 (POST 통신)
    const runBtn = document.getElementById('run-btn');
    const promptInput = document.getElementById('prompt-input');
    const repoUrlInput = document.getElementById('repo-url-input');

    if (runBtn && promptInput) {
        runBtn.addEventListener('click', async () => {
            const promptText = promptInput.value.trim();
            const repoUrl = repoUrlInput ? repoUrlInput.value.trim() : '';
            const githubToken = localStorage.getItem('github_token') || '';

            if (!promptText) {
                alert("AI에게 지시할 내용을 입력해주세요!");
                return;
            }

            runBtn.innerText = "Running...";
            runBtn.disabled = true;
            console.log("[Terminal] 백엔드로 작업 지시 전송 중...");

            try {
                const response = await fetch('/api/v1/agent/run-test', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ prompt: promptText, repoUrl: repoUrl, githubToken: githubToken })
                });

                if (response.status === 409) throw new Error("이미 에이전트가 실행 중입니다. 완료 후 다시 시도해주세요.");
                if (!response.ok) throw new Error("서버 연동 실패");

                console.log("[Terminal] 작업 지시 성공! 스트리밍 응답 대기 중...");
                // 실행 시간 측정을 위해 시작 시각 기록
                executionStartTime = Date.now();
                promptInput.value = ''; // 입력창 비우기

            } catch (error) {
                console.error(error);
                alert(error.message || "백엔드 서버로 명령을 전송할 수 없습니다.");
            } finally {
                // 처리가 끝나면 버튼 원상복구
                runBtn.innerText = "Run Agent";
                runBtn.disabled = false;
            }
        });
    }
});

// ============================================================================
// 7. 모달 및 Chart.js 시각화 제어 로직
// ============================================================================

let reliabilityChartInstance = null;

window.openChartModal = function() {
    const modal = document.getElementById('chart-modal');
    const modalContent = document.getElementById('chart-modal-content');
    if (!modal) return;
    
    // 모달 표시 애니메이션
    modal.classList.remove('opacity-0', 'pointer-events-none');
    modalContent.classList.remove('scale-95');
    modalContent.classList.add('scale-100');
    
    // Chart.js 캔버스 요소 가져오기
    const canvas = document.getElementById('reliabilityChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    
    // 기존 차트가 있다면 파괴 (새로 그리기 위해)
    if (reliabilityChartInstance) {
        reliabilityChartInstance.destroy();
    }
    
    // 네온 초록색 그라데이션(배경) 생성
    const gradient = ctx.createLinearGradient(0, 0, 0, 500);
    gradient.addColorStop(0, 'rgba(0, 255, 136, 0.4)');
    gradient.addColorStop(1, 'rgba(0, 255, 136, 0.0)');
    
    // Chart.js 인스턴스 생성
    reliabilityChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: ['Run 1', 'Run 2', 'Run 3', 'Run 4', 'Run 5', 'Run 6', 'Run 7', 'Run 8', 'Run 9', 'Latest'],
            datasets: [{
                label: 'Confidence Score',
                data: [65, 70, 68, 75, 82, 90, 88, 95, 100, 92], // 임시 Mock 데이터
                borderColor: '#00FF88', // 테마 포인트 컬러
                backgroundColor: gradient,
                borderWidth: 3,
                pointBackgroundColor: '#0A0A0A',
                pointBorderColor: '#00FF88',
                pointBorderWidth: 2,
                pointRadius: 5,
                pointHoverRadius: 8,
                fill: true,
                tension: 0.4 // 곡선 부드럽게
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: 'rgba(10, 10, 10, 0.9)',
                    titleColor: '#00FF88',
                    bodyColor: '#fff',
                    borderColor: 'rgba(0, 255, 136, 0.3)',
                    borderWidth: 1,
                    padding: 16,
                    titleFont: { size: 16, family: "'Pretendard', sans-serif" },
                    bodyFont: { size: 20, weight: 'bold', family: "'Pretendard', sans-serif" },
                    displayColors: false,
                    callbacks: {
                        label: function(context) {
                            return `Score: ${context.parsed.y}`;
                        }
                    }
                }
            },
            scales: {
                y: {
                    min: 0,
                    max: 100,
                    grid: {
                        color: 'rgba(255, 255, 255, 0.05)',
                        drawBorder: false
                    },
                    ticks: {
                        color: '#9CA3AF',
                        stepSize: 20,
                        font: { size: 16, family: "'Pretendard', sans-serif" }
                    }
                },
                x: {
                    grid: { display: false },
                    ticks: { 
                        color: '#9CA3AF',
                        font: { size: 16, family: "'Pretendard', sans-serif" }
                    }
                }
            }
        }
    });
};

window.closeChartModal = function() {
    const modal = document.getElementById('chart-modal');
    const modalContent = document.getElementById('chart-modal-content');
    if (!modal) return;
    
    // 모달 숨김 애니메이션
    modal.classList.add('opacity-0', 'pointer-events-none');
    modalContent.classList.remove('scale-100');
    modalContent.classList.add('scale-95');
};
