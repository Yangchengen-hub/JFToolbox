#!/usr/bin/env python3
"""
极风工具箱 — GitHub 上传 + 云端构建 APK

手机端最简流程：
  1. 装 Termux（F-Droid 搜）
  2. pkg install git python
  3. 把本文件和 JFToolbox.git.bundle 传到 Termux 家目录
  4. python3 upload_to_github.py
  5. 按提示粘贴 GitHub Token
  6. 等 5-8 分钟 → 浏览器下载 APK

获取 Token：浏览器打开 github.com/settings/tokens/new
勾选 repo + workflow → Generate → 复制 ghp_xxx
"""
import os, sys, json, subprocess, urllib.request, urllib.error

API = "https://api.github.com"
REPO = "JFToolbox"
DESC = "极风工具箱 — 安卓刷机/调试/玩机全能工具箱"
BUNDLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "JFToolbox.git.bundle")

def req(method, path, token, data=None):
    h = {"Authorization": f"token {token}", "Accept": "application/vnd.github.v3+json"}
    body = json.dumps(data).encode() if data else None
    if data: h["Content-Type"] = "application/json"
    r = urllib.request.Request(f"{API}{path}", data=body, headers=h, method=method)
    try:
        resp = urllib.request.urlopen(r, timeout=30)
        return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

def get_token():
    t = os.environ.get("GITHUB_TOKEN", "").strip()
    if t: return t
    f = os.path.join(os.path.dirname(os.path.abspath(__file__)), "github_token.txt")
    if os.path.exists(f): return open(f).read().strip()
    print("\n" + "="*50)
    print("  需要 GitHub Token（用于创建仓库 + 触发构建）")
    print("  获取方法（手机浏览器，1分钟）：")
    print("  1. 打开 github.com/settings/tokens/new")
    print("  2. Note 填 JFToolbox，勾选 repo + workflow")
    print("  3. 点 Generate → 复制 ghp_xxx")
    print("="*50 + "\n")
    t = input("粘贴 Token: ").strip()
    if not t: sys.exit("没有 Token 无法继续")
    open(f, "w").write(t)
    return t

def main():
    print("极风工具箱 — 云端构建\n")
    if not os.path.exists(BUNDLE):
        sys.exit(f"找不到 {BUNDLE}，请确保它在同一目录")

    token = get_token()
    code, me = req("GET", "/user", token)
    if code != 200: sys.exit(f"Token 无效: {me}")
    user = me["login"]
    print(f"已登录: {user}")

    # Create repo
    code, resp = req("POST", "/user/repos", token, {"name": REPO, "description": DESC, "private": False})
    clone_url = resp.get("clone_url", "")
    if code not in (200, 201):
        if code == 422:  # exists
            code, resp = req("GET", f"/repos/{user}/{REPO}", token)
            clone_url = resp.get("clone_url", "")
        else:
            sys.exit(f"建仓失败: {resp}")
    print(f"仓库: {clone_url}")

    auth_url = clone_url.replace("https://", f"https://{user}:{token}@")
    # Clone empty repo, fetch bundle, push
    import tempfile, shutil
    tmp = tempfile.mkdtemp()
    try:
        subprocess.run(["git", "clone", auth_url, tmp], check=True, capture_output=True)
        subprocess.run(["git", "fetch", BUNDLE, "main:main"], cwd=tmp, check=True)
        subprocess.run(["git", "checkout", "main"], cwd=tmp, check=True)
        subprocess.run(["git", "push", "origin", "main"], cwd=tmp, check=True)
        print("代码已推送 ✓")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    print(f"\n构建已触发！")
    print(f"查看进度: https://github.com/{user}/{REPO}/actions")
    print(f"下载 APK: 等绿色 ✅ → Artifacts → JFToolbox-APK")
    print(f"\n大约 5-8 分钟后出包。")

if __name__ == "__main__":
    main()
