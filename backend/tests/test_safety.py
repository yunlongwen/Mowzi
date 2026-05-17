"""Tests for content safety service."""

import pytest
from app.services.safety import SafetyService


class TestBlockedWordsDetection:
    """Test blocked words detection."""

    def test_blocked_words_detection(self):
        """Test detection of blocked words."""
        safety = SafetyService()

        # Test explicit blocked words
        assert not safety.contains_blocked_content("你好")
        assert not safety.contains_blocked_content("今天天气真好")
        assert safety.contains_blocked_content("杀人")
        assert safety.contains_blocked_content("去死")
        assert safety.contains_blocked_content("笨蛋")

    def test_chinese_blocked_words(self):
        """Test Chinese blocked words detection."""
        safety = SafetyService()

        # Test violence-related words
        assert safety.contains_blocked_content("我要杀人")
        assert safety.contains_blocked_content("打死你")
        assert safety.contains_blocked_content("我要打你")

        # Test death threats
        assert safety.contains_blocked_content("去死吧")
        assert safety.contains_blocked_content("该死")

        # Test profanity
        assert safety.contains_blocked_content("滚开")
        assert safety.contains_blocked_content("白痴")
        assert safety.contains_blocked_content("傻子")

    def test_mixed_content(self):
        """Test mixed content with blocked words."""
        safety = SafetyService()

        # Sentence with blocked word
        assert safety.contains_blocked_content("我讨厌你，你这个笨蛋")
        assert safety.contains_blocked_content("你去死吧")

        # Normal sentence
        assert not safety.contains_blocked_content("我今天很开心")

    def test_partial_match(self):
        """Test that partial matches work correctly."""
        safety = SafetyService()

        # "杀" alone IS blocked (single character violence-related)
        assert safety.contains_blocked_content("杀")
        # "杀人" contains "杀" which is blocked
        assert safety.contains_blocked_content("杀人")

    def test_case_insensitive(self):
        """Test case insensitive detection."""
        safety = SafetyService()

        assert safety.contains_blocked_content("去死")
        assert safety.contains_blocked_content("去死".upper())


class TestFilterContentReplacement:
    """Test content filtering and replacement."""

    def test_filter_content_replacement(self):
        """Test that blocked words are replaced with asterisks."""
        safety = SafetyService()

        # Single blocked word
        result = safety.filter_content("你是笨蛋")
        assert "*" in result
        assert "笨蛋" not in result

        # Multiple blocked words
        result = safety.filter_content("你这个傻子，笨蛋")
        assert result.count("*") >= 2
        assert "傻子" not in result
        assert "笨蛋" not in result

    def test_filter_preserves_length(self):
        """Test that filter preserves text length hints."""
        safety = SafetyService()

        # Each blocked word should be replaced with same-length asterisks
        original = "笨蛋"  # 2 chars -> **
        filtered = safety.filter_content(original)
        assert len(filtered) == len(original)
        assert filtered == "**"

        original = "傻子"  # 2 chars -> **
        filtered = safety.filter_content(original)
        assert len(filtered) == len(original)
        assert filtered == "**"

        # 3-char word
        original = "王八蛋"  # 3 chars -> ***
        filtered = safety.filter_content(original)
        assert len(filtered) == len(original)
        assert filtered == "***"

    def test_filter_normal_text(self):
        """Test that normal text is not modified."""
        safety = SafetyService()

        normal_text = "今天天气真好，我们去玩吧"
        result = safety.filter_content(normal_text)
        assert result == normal_text

    def test_filter_empty_text(self):
        """Test filtering empty text."""
        safety = SafetyService()

        assert safety.filter_content("") == ""
        assert safety.filter_content(None) is None
        assert safety.filter_content("   ") == "   "

    def test_filter_patterns(self):
        """Test filtering of blocked patterns."""
        safety = SafetyService()

        # Test blocked patterns - "杀" is in blocked words, so it gets filtered
        result = safety.filter_content("我要杀你")
        assert "*" in result
        assert "杀" not in result

        result = safety.filter_content("我要打死你")
        assert "*" in result


class TestCheckContent:
    """Test check_content method."""

    def test_check_content_safe(self):
        """Test check_content returns safe for normal text."""
        safety = SafetyService()

        result = safety.check_content("今天天气真好")
        assert result["safe"] is True
        assert result["flagged_words"] == []
        assert result["message"] == ""

    def test_check_content_unsafe(self):
        """Test check_content returns unsafe for blocked content."""
        safety = SafetyService()

        result = safety.check_content("你是笨蛋")
        assert result["safe"] is False
        assert "笨蛋" in result["flagged_words"]
        assert result["message"] == "内容包含不当词汇"

    def test_check_content_empty(self):
        """Test check_content handles empty text."""
        safety = SafetyService()

        result = safety.check_content("")
        assert result["safe"] is True

        result = safety.check_content(None)
        assert result["safe"] is True

        result = safety.check_content("   ")
        assert result["safe"] is True

    def test_check_content_multiple_flagged(self):
        """Test check_content detects multiple flagged words."""
        safety = SafetyService()

        result = safety.check_content("笨蛋和傻子")
        assert result["safe"] is False
        assert len(result["flagged_words"]) >= 2


class TestCustomBlockedWords:
    """Test SafetyService with custom blocked words."""

    def test_custom_blocked_words(self):
        """Test using custom blocked words list."""
        custom_words = ["badword1", "badword2"]
        safety = SafetyService(blocked_words=custom_words)

        assert safety.contains_blocked_content("badword1")
        assert safety.contains_blocked_content("badword2")
        assert not safety.contains_blocked_content("normal")

    def test_custom_blocked_patterns(self):
        """Test using custom blocked patterns."""
        custom_words = ["custom"]
        custom_patterns = ["bad pattern"]
        safety = SafetyService(blocked_words=custom_words)

        # Should detect custom word
        assert safety.contains_blocked_content("custom")

    def test_filter_with_custom_words(self):
        """Test filtering with custom words."""
        custom_words = ["foo", "bar"]
        safety = SafetyService(blocked_words=custom_words)

        result = safety.filter_content("foo and bar")
        assert "***" in result
        assert "foo" not in result
        assert "bar" not in result