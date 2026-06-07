import sys
import subprocess
import re
import json

def parse_token_value(val_str):
    val_str = val_str.lower().strip()
    if 'k' in val_str:
        return int(float(val_str.replace('k', '')) * 1000)
    try:
        return int(val_str)
    except ValueError:
        return 0

def extract_metrics(log_text):
    token_pattern = r"Tokens:\s*([\d\.]+k?)\s*sent,\s*([\d\.]+k?)\s*received"
    cost_pattern = r"Cost:.*?\$([\d\.]+)\s*(?:session|message)"
    token_matches = re.findall(token_pattern, log_text, re.IGNORECASE)
    cost_matches = re.findall(cost_pattern, log_text, re.IGNORECASE)
    
    total_tokens = 0
    final_cost = 0.0
    if token_matches:
        last_token = token_matches[-1]
        total_tokens = parse_token_value(last_token[0]) + parse_token_value(last_token[1])
    if cost_matches:
        final_cost = float(cost_matches[-1])
    return total_tokens, final_cost

def run_harness_pipeline(prompt):
    # 피드백 1 & 3 반영: 콤마 추가 및 실시간 스트리밍을 위해 Popen 사용
    aider_cmd = [
        "aider", 
        "--model", "gemini", 
        "--message", prompt, 
        "--stream", 
        "--no-pretty",
        "--yes"
    ]
    
    try:
        # Popen을 사용해 실시간으로 터미널에 출력(백엔드 SSE로 전달)하면서, 파이썬 변수에도 로그를 수집
        process = subprocess.Popen(aider_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        aider_log = ""
        for line in process.stdout:
            print(line, end="") # 실시간 스트리밍 출력
            aider_log += line
        process.wait()
        
        # 피드백 2 반영: Aider 자체가 비정상 종료되었는지 확인
        if process.returncode != 0:
            return json.dumps({
                "test_status": "FAIL", "total_tokens": 0, "cost": 0.0,
                "test_summary": f"에이전트 비정상 종료 (Exit Code: {process.returncode})"
            }, ensure_ascii=False)
            
    except Exception as e:
        return json.dumps({"test_status": "FAIL", "total_tokens": 0, "cost": 0.0, "test_summary": f"실행 에러: {str(e)}"}, ensure_ascii=False)

    total_tokens, cost = extract_metrics(aider_log)

    if "Applied edit to" not in aider_log:
        return json.dumps({
            "test_status": "SKIPPED", "total_tokens": total_tokens, "cost": cost,
            "test_summary": "코드 변경 사항이 존재하지 않아 단위 테스트 생략"
        }, ensure_ascii=False)

    try:
        pytest_result = subprocess.run(["pytest"], capture_output=True, text=True)
        
        # 피드백 4 반영: 테스트 파일이 없을 때(5)를 예외 처리
        if pytest_result.returncode == 0:
            test_status, test_summary = "PASS", "모든 하네스 단위 테스트 통과 완료"
        elif pytest_result.returncode == 5:
            test_status, test_summary = "SKIPPED", "수집된 단위 테스트가 없어 검증 생략"
        else:
            test_status, test_summary = "FAIL", "단위 테스트 검증 실패 (Assertion Error 혹은 구문 오류 발생)"
            
    except Exception as e:
        return json.dumps({"test_status": "FAIL", "total_tokens": total_tokens, "cost": cost, "test_summary": str(e)}, ensure_ascii=False)

    return json.dumps({"test_status": test_status, "total_tokens": total_tokens, "cost": cost, "test_summary": test_summary}, ensure_ascii=False)

if __name__ == "__main__":
    # 피드백 5 반영: 공백이 포함된 프롬프트 배열을 하나의 문자열로 안전하게 병합
    if len(sys.argv) > 1:
        target_prompt = " ".join(sys.argv[1:]).strip()
        print(run_harness_pipeline(target_prompt))
    else:
        print(json.dumps({"test_status": "FAIL", "total_tokens": 0, "cost": 0.0, "test_summary": "인자 누락"}, ensure_ascii=False))