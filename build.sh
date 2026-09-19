#!/usr/bin/env bash
#
# build.sh - builds the easyexcel-compare Spring Boot application (without running
# the test suite) and assembles an 'excelcompare/' deployment folder:
#
#   excelcompare/
#   ├── lib/easyexcel-compare-0.0.1.jar   (thin jar produced by -Djarmode=tools extract)
#   ├── lib/                          (all runtime dependency jars)
#   ├── config/excelcompare.properties
#   ├── data/source/  data/target/
#   ├── start.sh                      (Linux / macOS / Git Bash launcher)
#   └── start.bat                     (Windows launcher)
#
set -euo pipefail

# ---------------------------------------------------------------------------
# 0. Configuration (artifact coordinates are auto-detected from pom.xml)
# ---------------------------------------------------------------------------
DEST="excelcompare"
MAIN_CLASS="io.md2java.easyexcel.EasyexcelcomparatorApplication"

ARTIFACT_ID=$(sed -n 's:.*<artifactId>\([^<]*\)</artifactId>.*:\1:p' pom.xml | sed -n '2p')
VERSION=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | sed -n '2p')
[ -n "${ARTIFACT_ID}" ] || ARTIFACT_ID="easyexcel-compare"
[ -n "${VERSION}" ] || VERSION="0.0.1"
JAR_NAME="${ARTIFACT_ID}-${VERSION}.jar"
JAR_PATH="target/${JAR_NAME}"

command -v java >/dev/null 2>&1 || { echo "ERROR: java was not found on PATH"; exit 1; }

# ---------------------------------------------------------------------------
# 1. Maven build without test cases
# ---------------------------------------------------------------------------
echo "==> [1/4] Building ${ARTIFACT_ID} ${VERSION} (tests skipped)"
if [ -f ./mvnw ]; then
  sh ./mvnw -q clean package -DskipTests
else
  mvn -q clean package -DskipTests
fi
[ -f "${JAR_PATH}" ] || { echo "ERROR: expected build output ${JAR_PATH} is missing"; exit 1; }

# ---------------------------------------------------------------------------
# 2. Extract the boot jar (java -Djarmode=tools) into ./excelcompare
# ---------------------------------------------------------------------------
echo "==> [2/4] Extracting ${JAR_PATH} into ./${DEST}"
rm -rf "${DEST}"
mkdir -p "${DEST}"
java -Djarmode=tools -jar "${JAR_PATH}" extract --destination "${DEST}/bootstrap"


# ---------------------------------------------------------------------------
# 3. Copy application.properties -> excelcompare/config/excelcompare.properties
#    and copy the data directory (and its sub directories) into ./excelcompare
# ---------------------------------------------------------------------------
echo "==> [3/4] Copying config and data into ./${DEST}"
mkdir -p "${DEST}/config"
cp src/main/resources/config/excelcompare.properties "${DEST}/config/excelcompare.properties"
[ -d data ] && cp -R data "${DEST}/"

# ---------------------------------------------------------------------------
# 4. Create start.sh and start.bat launchers
# ---------------------------------------------------------------------------
echo "==> [4/4] Creating ./${DEST}/start.sh and ./${DEST}/start.bat"
cat > "${DEST}/start.sh" <<EOF
#!/usr/bin/env bash
# Start the ${ARTIFACT_ID} application from the extracted folder.
cd "\$(dirname "\$0")" || exit 1
# "." keeps ./config/excelcompare.properties and ./data on the classpath so that
# spring.config.import=classpath:/config/excelcompare.properties is picked up.
exec java -cp ".;bootstrap/${JAR_NAME}" ${MAIN_CLASS} "\$@"
EOF
chmod +x "${DEST}/start.sh"

cat > "${DEST}/start.bat" <<EOF
@echo off
rem Start the ${ARTIFACT_ID} application from the extracted folder.
cd /d "%~dp0"
java -cp ".;bootstrap/${JAR_NAME}" ${MAIN_CLASS} %*
EOF

echo ""
echo "==> Done. Deployment folder: ${DEST}"
echo "    Run with:   sh ${DEST}/start.sh        (Linux / macOS / Git Bash)"
echo "    Run with:   ${DEST}\\start.bat  (Windows)"