import sys
import subprocess
import re
import json

def parse_token_value(val_str):
    """'3.3k' 또는 '528' 형태의 문자열을 정수형(int)으로 안전하게 변환하는 파서"""
    val_str = val_str.lower().strip()
    if 'k' in val_str:
        # 'k'를 제거하고 숫자로 바꾼 뒤 1000을 곱함
        return int(float(val_str.replace('k', '')) * 1000)
    try:
        return int(val_str)
    except ValueError:
        return 0

def extract_metrics(log_text):
    """Aider 출력 로그에서 최종 누적 토큰 수량과 세션 비용을 추출하는 함수"""
    # Tokens: 3.3k sent, 783 received 형태 매칭 정규식
    token_pattern = r"Tokens:\s*([\d\.]+k?)\s*sent,\s*([\d\.]+k?)\s*received"
    # Cost: ... $0.08 session 또는 메시지 비용 매칭 정규식
    cost_pattern = r"Cost:.*?\$([\d\.]+)\s*(?:session|message)"

    token_matches = re.findall(token_pattern, log_text, re.IGNORECASE)
    cost_matches = re.findall(cost_pattern, log_text, re.IGNORECASE)

    total_tokens = 0
    final_cost = 0.0

    # 여러 번 수행된 대화가 있을 경우, 항상 가장 마지막(최신 누적) 통계를 사용
    if token_matches:
        last_token = token_matches[-1]
        total_tokens = parse_token_value(last_token[0]) + parse_token_value(last_token[1])

    if cost_matches:
        final_cost = float(cost_matches[-1])

    return total_tokens, final_cost

def run_harness_pipeline(prompt):
    """Aider 구동 -> 코드 변경 유무 확인 -> Pytest 검증 트리거를 제어하는 메인 파이프라인"""
    
    # 1. CLI 단발성 모드로 Aider 실행 (성현님이 검증한 실시간 스트리밍 및 출력 최적화 플래그)
    try:
        aider_cmd = [
            "aider", 
            "--model", "gemini/gemini-1.5-pro", 
            "--message", prompt, 
            "--stream", 
            "--no-pretty"
            "--yes" # 👈 Aider의 모든 질문에 자동 'Y' 입력!
        ]
        # 백엔드 콘솔 출력을 캡처하기 위해 capture_output 활성화
        process_result = subprocess.run(aider_cmd, capture_output=True, text=True, check=False)
        aider_log = process_result.stdout + "\n" + process_result.stderr
    except Exception as e:
        return json.dumps({
            "test_status": "FAIL",
            "total_tokens": 0,
            "cost": 0.0,
            "test_summary": f"에이전트 내부 실행 에러: {str(e)}"
        }, ensure_ascii=False)

    # 로그에서 토큰 수 및 비용 파싱
    total_tokens, cost = extract_metrics(aider_log)

    # 2. 코드 수정 사항 감지 (성현님 로그 분석 결과 'Applied edit to'가 기준점이 됨)
    if "Applied edit to" not in aider_log:
        return json.dumps({
            "test_status": "SKIPPED",
            "total_tokens": total_tokens,
            "cost": cost,
            "test_summary": "코드 변경 사항이 존재하지 않아 단위 테스트 생략"
        }, ensure_ascii=False)

    # 3. 변경 사항이 있을 경우 자동 검증 (Pytest) 트리거
    try:
        # 텍스트 오진을 방지하기 위해 OS 커맨드 자체의 Return Code를 기준으로 판별
        pytest_result = subprocess.run(["pytest"], capture_output=True, text=True)
        
        # exit_code가 0이면 모든 테스트 성공 Pass, 그 외(1, 2 등)는 실패 Fail 처리
        if pytest_result.returncode == 0:
            test_status = "PASS"
            test_summary = "모든 하네스 단위 테스트 통과 완료"
        else:
            test_status = "FAIL"
            test_summary = "단위 테스트 검증 실패 (Assertion Error 혹은 구문 오류 발생)"
            
    except Exception as e:
        return json.dumps({
            "test_status": "FAIL",
            "total_tokens": total_tokens,
            "cost": cost,
            "test_summary": f"테스트 시스템 구동 에러: {str(e)}"
        }, ensure_ascii=False)

    # 4. 자바 서버와 약속된 JSON 포맷 반환
    return json.dumps({
        "test_status": test_status,
        "total_tokens": total_tokens,
        "cost": cost,
        "test_summary": test_summary
    }, ensure_ascii=False)

if __name__ == "__main__":
    # Java의 ProcessBuilder 아규먼트로부터 유저 프롬프트를 넘겨받음
    if len(sys.argv) > 1:
        target_prompt = sys.argv[1]
        print(run_harness_pipeline(target_prompt))
    else:
        print(json.dumps({
            "test_status": "FAIL",
            "total_tokens": 0,
            "cost": 0.0,
            "test_summary": "시스템 오류: 실행 인자(Prompt)가 누락되었습니다."
        }, ensure_ascii=False))