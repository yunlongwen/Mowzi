"""Tests for config API endpoints (characters list)."""

import pytest

from app.models.character import AICharacter


class TestCharactersAPI:
    """Tests for GET /api/v1/config/characters."""

    def test_get_characters_empty_list(self, client, db_session):
        """Test getting characters when none exist returns empty list."""
        response = client.get("/api/v1/config/characters")
        assert response.status_code == 200
        data = response.json()
        assert "characters" in data
        assert data["characters"] == []

    def test_get_characters_returns_seeded_characters(self, client, db_session):
        """Test getting characters returns all seeded characters."""
        char1 = AICharacter(
            id=1,
            name="小智",
            avatar_url="https://example.com/xiaozhi.png",
            description="活泼好奇的小机器人",
            system_prompt="你是小智",
            tts_voice_name="xiaoyan",
        )
        char2 = AICharacter(
            id=2,
            name="小花",
            avatar_url="https://example.com/xiaohua.png",
            description="温柔善良的小花仙",
            system_prompt="你是小花",
            tts_voice_name="xiaoyan",
        )
        db_session.add_all([char1, char2])
        db_session.commit()

        response = client.get("/api/v1/config/characters")
        assert response.status_code == 200
        data = response.json()
        assert len(data["characters"]) == 2

    def test_get_characters_data_structure(self, client, db_session):
        """Test that each character has the required fields."""
        char = AICharacter(
            id=1,
            name="小智",
            avatar_url="https://example.com/xiaozhi.png",
            description="活泼好奇的小机器人",
            system_prompt="你是小智",
            tts_voice_name="xiaoyan",
        )
        db_session.add(char)
        db_session.commit()

        response = client.get("/api/v1/config/characters")
        assert response.status_code == 200
        data = response.json()
        assert len(data["characters"]) == 1

        c = data["characters"][0]
        assert "id" in c
        assert "name" in c
        assert "avatar_url" in c
        assert "description" in c
        assert c["id"] == 1
        assert c["name"] == "小智"
        assert c["avatar_url"] == "https://example.com/xiaozhi.png"
        assert c["description"] == "活泼好奇的小机器人"

    def test_get_characters_excludes_internal_fields(self, client, db_session):
        """Test that system_prompt and tts_voice_name are not returned."""
        char = AICharacter(
            id=1,
            name="小智",
            avatar_url=None,
            description="测试角色",
            system_prompt="秘密提示词",
            tts_voice_name="xiaoyan",
        )
        db_session.add(char)
        db_session.commit()

        response = client.get("/api/v1/config/characters")
        data = response.json()
        c = data["characters"][0]
        assert "system_prompt" not in c
        assert "tts_voice_name" not in c

    def test_get_characters_no_auth_required(self, client, db_session):
        """Test that character list is accessible without auth."""
        response = client.get("/api/v1/config/characters")
        # Should not return 401 or 422
        assert response.status_code == 200
