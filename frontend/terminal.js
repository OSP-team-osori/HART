/**
 * ============================================================================
 * [HART TADD] 가상 터미널 실시간 스트리밍 제어 및 백엔드 POST 연동
 * ============================================================================
 */

const CONFIG = {
    // ✅ 실제 백엔드와 통신하기 위해 false로 유지
    USE_MOCK: false,
    // ✅ 백엔드 SSE 스트리밍 엔드포인트 주소 고정 (8090 포트)
    API_URL: 'http://localhost:8090/api/v1/agent/stream'
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
                currentLineElement.className = "text-blue-400 font-semibold whitespace-pre-wrap";
            } else if (text.includes("[WARN]")) {
                currentLineElement.className = "text-yellow-400 font-semibold whitespace-pre-wrap";
            } else if (text.includes("[SUCCESS]")) {
                currentLineElement.className = "text-secondary font-bold whitespace-pre-wrap";
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

// 5. 엔트리 포인트 및 POST 버튼 로직
document.addEventListener("DOMContentLoaded", () => {
    // 백엔드 스트리밍 채널 활성화
    if (CONFIG.USE_MOCK) {
        runMockTerminal();
    } else {
        connectRealBackend();
    }

    // 6. 버튼 클릭 시 프롬프트를 백엔드로 전송 (POST 통신)
    const runBtn = document.getElementById('run-btn');
    const promptInput = document.getElementById('prompt-input');

    if (runBtn && promptInput) {
        runBtn.addEventListener('click', async () => {
            const promptText = promptInput.value.trim();
            
            if (!promptText) {
                alert("AI에게 지시할 내용을 입력해주세요!");
                return;
            }

            // 전송 중 UI 상태 변경 방지
            runBtn.innerText = "Running...";
            runBtn.disabled = true;
            console.log("[Terminal] 백엔드로 작업 지시 전송 중...");

            try {
                // ✅ 프롬프트를 8090 백엔드 포트로 발사!
                const response = await fetch('http://localhost:8090/api/v1/agent/run-test', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ prompt: promptText })
                });

                if (response.status === 409) throw new Error("이미 에이전트가 실행 중입니다. 완료 후 다시 시도해주세요.");
                if (!response.ok) throw new Error("서버 연동 실패");

                console.log("[Terminal] 작업 지시 성공! 스트리밍 응답 대기 중...");
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