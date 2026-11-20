#!/data/data/com.termux/files/usr/bin/bash
# Kullanım: bash push.sh [repo-adı]
set -e
REPO="${1:-NowBarDemo}"
git init -q 2>/dev/null || true
git add -A
git commit -qm "Now Bar demo" || true
git branch -M main
if command -v gh >/dev/null 2>&1; then
  gh repo create "$REPO" --public --source=. --remote=origin --push
else
  USER=$(git config user.name)
  git remote add origin "https://github.com/$USER/$REPO.git" 2>/dev/null || true
  echo "gh yok: önce github.com'da '$REPO' adlı boş repo aç, sonra: git push -u origin main"
fi
