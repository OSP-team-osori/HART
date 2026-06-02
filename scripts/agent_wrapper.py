#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import re
import json
import argparse
import sys
import os

def parse_tokens(token_str):
    """
    토큰 수 표현(예: '1,250', '3.5k')을 정수로 변환하는 헬퍼 함수
    """
    if not token_str:
        return 0
    token_str = token_str.lower().replace(',', '').strip()
    if 'k' in token_str:
        try:
            return int(float(token_str.replace('k', '')) * 1000)
        except ValueError:
            return 0
    try:
        return int(token_str)
    except ValueError:
        return 0

def analyze_logs(log_content):
    """
    로그 텍스트 전체를 정규식으로 분석하여 토큰, 비용, 수정 여부, 테스트 상태를 판정합니다.
    """
    # 1. 토큰 및 비용 추출 정규식
    # 예: Tokens: 1,250 sent, 520 received. Cost: $0.0198
    token_pattern = re.compile(
        r"Tokens:\s*(?P<sent>[\d\.,]+k?)\s*sent,\s*(?P<recv>[\d\.,]+k?)\s*received",
        re.IGNORECASE
    )
    cost_pattern = re.compile(
        r"Cost:.*?\$(?P<cost>[\d\.]+)",
        re.IGNORECASE
    )
    
    # 2. 코드 수정 여부 감지 정규식
    # 예: Applied edit to src/auth.py 또는 Commit 1234abc:
    edit_pattern = re.compile(r"Applied edit to\s+\S+", re.IGNORECASE)
    commit_pattern = re.compile(r"Commit\s+[a-f0-9]+:", re.IGNORECASE)
    no_change_pattern = re.compile(r"No changes made\.", re.IGNORECASE)
    
    # 3. 테스트(pytest) 결과 파싱 정규식
    # 예: === 1 passed in 0.12s ===, === 1 failed in 0.25s ===
    test_pass_pattern = re.compile(r"==+\s*(?P<passed>\d+)\s*passed[^\n]*==+", re.IGNORECASE)
    test_fail_pattern = re.compile(r"==+\s*(?P<failed>\d+)\s*failed[^\n]*==+", re.IGNORECASE)
    
    # 기본값 설정
    total_tokens = 0
    cost = 0.0
    code_modified = False
    test_status = "SKIPPED"
    test_summary = "No changes detected, test skipped."
    
    # 토큰 검색
    token_match = token_pattern.search(log_content)
    if token_match:
        sent_tokens = parse_tokens(token_match.group("sent"))
        recv_tokens = parse_tokens(token_match.group("recv"))
        total_tokens = sent_tokens + recv_tokens
        
    # 비용 검색
    cost_match = cost_pattern.search(log_content)
    if cost_match:
        try:
            cost = float(cost_match.group("cost"))
        except ValueError:
            cost = 0.0
            
    # 코드 수정 여부 체크
    has_edit = edit_pattern.search(log_content) is not None
    has_commit = commit_pattern.search(log_content) is not None
    has_no_change = no_change_pattern.search(log_content) is not None
    
    if (has_edit or has_commit) and not has_no_change:
        code_modified = True
        
    # 테스트 결과 파싱 및 최종 상태 결정
    if code_modified:
        # pytest 결과 파싱
        pass_match = test_pass_pattern.search(log_content)
        fail_match = test_fail_pattern.search(log_content)
        
        passed_count = int(pass_match.group("passed")) if pass_match else 0
        failed_count = int(fail_match.group("failed")) if fail_match else 0
        
        if failed_count > 0:
            test_status = "FAIL"
            test_summary = f"{failed_count} test(s) failed. Code changes failed validation."
        elif passed_count > 0:
            test_status = "PASS"
            test_summary = f"{passed_count} test(s) passed successfully. Code changes validated."
        else:
            # 코드 수정은 있었으나 테스트 결과가 파싱되지 않는 엣지 케이스
            test_status = "FAIL"
            test_summary = "Code was modified, but no valid test results were detected in logs."
    else:
        test_status = "SKIPPED"
        test_summary = "Code was not modified. Testing is skipped."
        
    # 최종 결과 반환
    return {
        "test_status": test_status,
        "total_tokens": total_tokens,
        "cost": round(cost, 6),
        "test_summary": test_summary
    }

def main():
    parser = argparse.ArgumentParser(description="Agent Execution Log Parser Wrapper")
    parser.add_argument("--mock", type=str, help="Path to a dummy log file to simulate parsing")
    
    args = parser.parse_args()
    
    if args.mock:
        # 시뮬레이션 모드 (더미 로그 파일 읽어 처리)
        if not os.path.exists(args.mock):
            print(f"Error: Mock file '{args.mock}' not found.", file=sys.stderr)
            sys.exit(1)
            
        with open(args.mock, "r", encoding="utf-8") as f:
            log_content = f.read()
            
        # 백엔드 콘솔에 로그 스트리밍을 흉내 내어 로그 전체를 표준 출력으로 흘림
        print(log_content)
        
        # 로그 분석 수행
        result = analyze_logs(log_content)
        
        # 최종 결과를 표준 출력 맨 마지막 줄에 JSON으로 출력
        print(json.dumps(result))
    else:
        # 실제 Aider 실행 연동부 (향후 필요 시 확장될 영역)
        print("Real execution mode is not implemented yet. Please use --mock <filepath> to test.")
        sys.exit(1)

if __name__ == "__main__":
    main()
