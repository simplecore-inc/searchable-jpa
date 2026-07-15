#!/usr/bin/env bash
# Searchable JPA Documentation Build Script
# Copies and transforms documentation for Docsify deployment
set -e

BUILD_DIR="build-docs"
LOCALES=("en" "ko")
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Use DOCS_VERSION env var if set, otherwise extract from build.gradle
if [ -n "$DOCS_VERSION" ]; then
  VERSION="$DOCS_VERSION"
else
  VERSION=$(grep "version = " build.gradle | head -1 | sed "s/.*version = '\([^']*\)'.*/\1/")
fi
echo "Building Searchable JPA documentation... (v${VERSION})"

# 1. Initialize build directory
rm -rf "$BUILD_DIR"
for locale in "${LOCALES[@]}"; do
  mkdir -p "$BUILD_DIR/$locale"
done

# 2. Copy Docsify configuration files
echo "Copying Docsify configuration files..."
cp docs/index.html "$BUILD_DIR/"
cp docs/.nojekyll "$BUILD_DIR/"
cp docs/_coverpage.md "$BUILD_DIR/"
cp docs/_navbar.md "$BUILD_DIR/"

# English is the default locale, so its sidebar also serves as the root sidebar.
cp docs/en/_sidebar.md "$BUILD_DIR/en/"
cp docs/en/_sidebar.md "$BUILD_DIR/_sidebar.md"
cp docs/ko/_sidebar.md "$BUILD_DIR/ko/"

# 2.5. Replace version placeholder (escape hyphens for shields.io badge)
VERSION_ESCAPED=$(echo "$VERSION" | sed 's/-/--/g')
sed -i.bak "s/{{VERSION}}/${VERSION_ESCAPED}/g" "$BUILD_DIR/_coverpage.md" && rm -f "$BUILD_DIR/_coverpage.md.bak"

# 3. Copy all documentation and image assets for each locale
echo "Copying documentation files..."
for locale in "${LOCALES[@]}"; do
  find "docs/$locale" -maxdepth 1 -name "*.md" -type f -exec cp {} "$BUILD_DIR/$locale/" \;

  if [ -d "docs/$locale/_images" ]; then
    mkdir -p "$BUILD_DIR/$locale/_images"
    cp docs/"$locale"/_images/*.svg "$BUILD_DIR/$locale/_images/" 2>/dev/null || true
  fi
done

# 4. Link conversion function
convert_links() {
  local file="$1"

  # Get the directory path relative to build-docs (e.g., ko, en)
  local dir_path
  dir_path=$(dirname "${file#$BUILD_DIR/}")

  # Convert ./ relative links to absolute paths based on file location
  sed -i.bak "s|(\./|($dir_path/|g" "$file" && rm -f "${file}.bak"

  # Common link conversions
  sed -i.bak -e 's|(/en/|(en/|g' \
             -e 's|(/ko/|(ko/|g' \
             -e 's|\.\./\.\./LICENSE|https://github.com/simplecore-inc/searchable-jpa/blob/master/LICENSE|g' \
             "$file" && rm -f "${file}.bak"
}

# 5. Apply link conversion to all markdown files
echo "Converting links..."
find "$BUILD_DIR" -name "*.md" -type f | while read -r file; do
  convert_links "$file"
done

# 6. Count files
total_files=$(find "$BUILD_DIR" -name "*.md" -type f | wc -l | tr -d ' ')

echo ""
echo "Documentation build completed!"
echo "  Output directory: $BUILD_DIR"
echo "  Total markdown files: $total_files"
echo ""
echo "To preview locally:"
echo "  cd $BUILD_DIR && python -m http.server 3000"
echo "  Then open http://localhost:3000"
