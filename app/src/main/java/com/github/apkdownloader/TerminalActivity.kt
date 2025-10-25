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
            │  명령어 실행 환경                    │
            └─────────────────────────────────────┘

            📁 앱 디렉토리: ${dirInfo.home}
            📁 실행 파일: ${dirInfo.bin}

            ⚠️  중요: 이것은 Termux가 아닙니다!

            이 터미널은:
            ✅ 이미 설치된 명령어 실행 가능
            ✅ Termux Python 사용 가능 (설치되어 있다면)
            ❌ pkg 명령어 사용 불가 (Termux 전용)
            ❌ 패키지 설치 불가

            💡 사용 가능한 명령어:
            - help              도움말
            - info              시스템 정보
            - python3 --version Python 확인
            - pip3 list         설치된 패키지
            - which python3     Python 경로
            - ls / pwd / cd     파일 탐색

            📦 Python 설치하려면:

            1. Termux 앱 설치 (Google Play)
            2. **Termux 앱을 열고** 실행:
               $ pkg install python
               $ pip install google-generativeai
               $ pip install anthropic

            3. 설치 후 이 앱의 Settings에서 확인

            ⚠️  이 터미널에서 "pkg" 입력하면 에러남!
            → Termux 앱을 직접 열어서 설치하세요!

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

            === 이 터미널에서 가능한 명령어 ===
            help        - 이 도움말 표시
            clear       - 화면 지우기
            info        - 시스템 정보
            pwd         - 현재 디렉토리
            ls          - 파일 목록
            cd [dir]    - 디렉토리 이동

            === Python 확인 (이미 설치된 경우) ===
            python3 --version       - Python 버전
            pip3 --version          - pip 버전
            pip3 list              - 설치된 패키지 목록
            which python3           - Python 위치

            ⚠️  이 터미널에서 불가능한 것들:
            ❌ pkg install python       (pkg는 Termux 전용!)
            ❌ pip install [package]    (권한 없음)
            ❌ apt, yum, dnf           (패키지 관리자 없음)

            === Python 설치 방법 ===

            1. Google Play에서 Termux 앱 설치
            2. **Termux 앱을 열기** (이 터미널 아님!)
            3. Termux에서 실행:
               $ pkg install python
               $ pip install google-generativeai
               $ pip install anthropic

            4. 이 앱 Settings → Python 상태 확인

            === 왜 이렇게 복잡하나요? ===

            Android는 보안을 위해 앱마다 격리되어 있습니다.
            이 앱은 명령어를 **실행**만 할 수 있고,
            패키지를 **설치**할 권한은 없습니다.

            Termux = 완전한 Linux 환경 (설치 가능)
            이 터미널 = 명령어 실행기 (실행만 가능)

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
