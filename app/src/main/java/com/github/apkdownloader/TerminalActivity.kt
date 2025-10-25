package com.github.apkdownloader

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.apkdownloader.ai.AppTerminal
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class TerminalActivity : AppCompatActivity() {

    private lateinit var terminalOutput: TextView
    private lateinit var commandInput: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var appTerminal: AppTerminal

    private val outputHistory = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Terminal"

        appTerminal = AppTerminal(this)

        terminalOutput = findViewById(R.id.terminalOutput)
        commandInput = findViewById(R.id.commandInput)
        scrollView = findViewById(R.id.scrollView)

        setupCommandInput()
        showWelcomeMessage()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupCommandInput() {
        commandInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                executeCommand()
                true
            } else {
                false
            }
        }
    }

    private fun showWelcomeMessage() {
        val dirInfo = appTerminal.getDirectoryInfo()
        val welcomeMsg = """
            ┌─────────────────────────────────────┐
            │  GitHub APK Downloader Terminal     │
            │  Python 설치 및 명령어 실행         │
            └─────────────────────────────────────┘

            📁 홈 디렉토리: ${dirInfo.home}
            📁 실행 파일: ${dirInfo.bin}
            📁 라이브러리: ${dirInfo.lib}

            💡 사용 가능한 명령어:
            - help              도움말 표시
            - python3 --version Python 버전 확인
            - pip3 --version    pip 버전 확인
            - which python3     Python 경로 확인
            - ls                파일 목록
            - pwd               현재 경로
            - echo "text"       텍스트 출력

            ⚠️  Python 수동 설치 방법:

            1. Termux 앱 설치 (Google Play에서)
            2. Termux에서 실행:
               $ pkg install python
               $ pip install google-generativeai gemini-cli

            3. 또는 앱의 bin 폴더에 Python 바이너리 복사

            명령어를 입력하세요...

        """.trimIndent()

        appendOutput(welcomeMsg)
    }

    private fun executeCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isEmpty()) return

        // Clear input
        commandInput.text.clear()

        // Show command in output
        appendOutput("$ $command\n")

        // Handle special commands
        when (command) {
            "help" -> {
                showHelpMessage()
                return
            }
            "clear" -> {
                outputHistory.clear()
                terminalOutput.text = ""
                return
            }
            "info" -> {
                showSystemInfo()
                return
            }
        }

        // Execute command
        lifecycleScope.launch {
            try {
                val result = appTerminal.execute(command)

                if (result.success) {
                    appendOutput(result.output)
                    appendOutput("\n✅ 종료 코드: ${result.exitCode}\n\n")
                } else {
                    appendOutput(result.output)
                    appendOutput("\n❌ 종료 코드: ${result.exitCode}\n\n")
                }

            } catch (e: Exception) {
                appendOutput("❌ 에러: ${e.message}\n\n")
            }
        }
    }

    private fun showHelpMessage() {
        val helpMsg = """
            📚 도움말

            === 기본 명령어 ===
            help        - 이 도움말 표시
            clear       - 화면 지우기
            info        - 시스템 정보
            pwd         - 현재 디렉토리
            ls          - 파일 목록
            cd [dir]    - 디렉토리 이동

            === Python 관련 ===
            python3 --version       - Python 버전
            pip3 --version          - pip 버전
            which python3           - Python 위치

            === 설치 명령어 (Termux 필요) ===
            pkg install python      - Python 설치
            pip install [package]   - Python 패키지 설치

            === Gemini CLI 설치 ===
            pip install google-generativeai gemini-cli

            === Claude CLI 설치 ===
            pip install anthropic-cli

            === Android 빌드 도구 ===
            앱의 Settings에서 자동 설치 가능

        """.trimIndent()

        appendOutput(helpMsg + "\n")
    }

    private fun showSystemInfo() {
        val dirInfo = appTerminal.getDirectoryInfo()
        val info = """
            🖥️  시스템 정보

            📁 HOME: ${dirInfo.home}
            📁 BIN: ${dirInfo.bin}
            📁 LIB: ${dirInfo.lib}
            📁 CACHE: ${dirInfo.cache}
            📁 NATIVE_LIB: ${dirInfo.nativeLib}

            🤖 OS: Android ${android.os.Build.VERSION.RELEASE}
            📱 디바이스: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            🏗️  ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}

        """.trimIndent()

        appendOutput(info + "\n")
    }

    private fun appendOutput(text: String) {
        outputHistory.append(text)
        terminalOutput.text = outputHistory.toString()

        // Auto-scroll to bottom
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
