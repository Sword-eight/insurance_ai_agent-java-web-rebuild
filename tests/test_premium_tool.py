"""PremiumCalculatorTool 的依赖注入和委托测试。"""

from unittest.mock import Mock

from services.premium_service import PremiumService
from tools.premium_calculator_tool import PremiumCalculatorTool


def _premium_result() -> dict:
    return {
        "success": True,
        "occupation_class": "1类",
        "occupation_name": "办公室职员",
        "age": 30,
        "gender": "男",
        "coverage_amount": 50,
        "insurance_term": "20年",
        "breakdown": {
            "base_rate_per_10k": 35,
            "gender_factor": 1.0,
            "age_factor": 1.0,
            "term_factor": 0.78,
        },
        "annual_premium": 1365.0,
        "monthly_premium": 113.75,
    }


def test_tool_keeps_injected_service_instance() -> None:
    injected_service = Mock(spec=PremiumService)

    tool = PremiumCalculatorTool(premium_service=injected_service)

    assert tool._service is injected_service


def test_tool_delegates_all_arguments_to_service() -> None:
    injected_service = Mock(spec=PremiumService)
    injected_service.calculate.return_value = _premium_result()
    tool = PremiumCalculatorTool(premium_service=injected_service)

    tool._run(
        age=30,
        gender="男",
        coverage_amount=50,
        insurance_term="20年",
        occupation_class="1类",
    )

    injected_service.calculate.assert_called_once_with(
        age=30,
        gender="男",
        coverage_amount=50,
        insurance_term="20年",
        occupation_class="1类",
    )


def test_tool_formats_service_result() -> None:
    injected_service = Mock(spec=PremiumService)
    injected_service.calculate.return_value = _premium_result()
    tool = PremiumCalculatorTool(premium_service=injected_service)

    result = tool._run(
        age=30,
        gender="男",
        coverage_amount=50,
        insurance_term="20年",
        occupation_class="1类",
    )

    assert "年度保费: 1365.0元/年" in result
    assert "月度保费: 113.75元/月" in result


def test_tool_returns_failure_text_when_service_raises() -> None:
    injected_service = Mock(spec=PremiumService)
    injected_service.calculate.side_effect = ValueError("测试异常")
    tool = PremiumCalculatorTool(premium_service=injected_service)

    result = tool._run(
        age=30,
        gender="男",
        coverage_amount=50,
        insurance_term="20年",
        occupation_class="1类",
    )

    assert result == "保费估算失败: 测试异常"
