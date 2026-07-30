"""PremiumService 的纯业务单元测试。"""

import pytest

from services.premium_service import PremiumService


def test_calculate_returns_expected_breakdown_and_premium() -> None:
    service = PremiumService()

    result = service.calculate(
        age=30,
        gender="男",
        coverage_amount=50,
        insurance_term="20年",
        occupation_class="1类",
    )

    assert result["success"] is True
    assert result["annual_premium"] == 1365.0
    assert result["monthly_premium"] == 113.75
    assert result["breakdown"] == {
        "base_rate_per_10k": 35,
        "gender_factor": 1.0,
        "age_factor": 1.0,
        "term_factor": 0.78,
    }


@pytest.mark.parametrize("age", [16, 60])
def test_calculate_accepts_boundary_ages(age: int) -> None:
    service = PremiumService()

    result = service.calculate(
        age=age,
        gender="女",
        coverage_amount=10,
        insurance_term="10年",
        occupation_class="2类",
    )

    assert result["age"] == age
    assert result["annual_premium"] > 0


@pytest.mark.parametrize("age", [15, 61])
def test_calculate_rejects_invalid_age(age: int) -> None:
    service = PremiumService()

    with pytest.raises(ValueError, match="年龄超出范围"):
        service.calculate(
            age=age,
            gender="男",
            coverage_amount=50,
            insurance_term="20年",
            occupation_class="1类",
        )


@pytest.mark.parametrize(
    ("field", "invalid_value", "message"),
    [
        ("gender", "未知", "性别参数无效"),
        ("insurance_term", "5年", "保险期限参数无效"),
        ("occupation_class", "9类", "未知职业类别"),
    ],
)
def test_calculate_rejects_invalid_enum_parameters(
    field: str,
    invalid_value: str,
    message: str,
) -> None:
    service = PremiumService()
    arguments = {
        "age": 30,
        "gender": "男",
        "coverage_amount": 50,
        "insurance_term": "20年",
        "occupation_class": "1类",
    }
    arguments[field] = invalid_value

    with pytest.raises(ValueError, match=message):
        service.calculate(**arguments)
