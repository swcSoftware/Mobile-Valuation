"""
Canonical concept -> ordered list of XBRL tags to try.

Companies tag the same economic line item differently (and change tags across years).
The first tag in each list that has data for a period wins; the chosen tag is always
recorded on the resulting value so the UI can show exactly which SEC line item was used.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class Kind(str, Enum):
    FLOW = "flow"        # income / cash-flow items: have start+end (duration)
    INSTANT = "instant"  # balance-sheet items: end only


@dataclass(frozen=True)
class Concept:
    key: str
    label: str
    kind: Kind
    tags: tuple[str, ...]
    unit: str = "USD"
    taxonomy: str = "us-gaap"
    statement: str = "income"
    notes: str = ""
    # How much its absence matters (coverage probe + "missing concepts" warning):
    #   required — every operating company reports it; absence is a real gap
    #   expected — most do; absence disables a model input
    #   optional — legitimately absent for many filers (no inventory, no dividend, no property sales)
    requirement: str = "optional"


CONCEPTS: list[Concept] = [
    # ---- Income statement -------------------------------------------------
    Concept("revenue", "Revenue", Kind.FLOW, (
        "Revenues",
        "RevenueFromContractWithCustomerExcludingAssessedTax",
        "SalesRevenueNet",
        "RevenueFromContractWithCustomerIncludingAssessedTax",
        "RevenuesNetOfInterestExpense",
    ), requirement="required"),
    Concept("cost_of_revenue", "Cost of revenue", Kind.FLOW, (
        "CostOfRevenue", "CostOfGoodsAndServicesSold", "CostOfGoodsSold",
    )),
    Concept("operating_income", "Operating income (EBIT)", Kind.FLOW, (
        "OperatingIncomeLoss",
    ), requirement="expected"),
    Concept("pretax_income", "Pre-tax income", Kind.FLOW, (
        "IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest",
        "IncomeLossFromContinuingOperationsBeforeIncomeTaxesMinorityInterestAndIncomeLossFromEquityMethodInvestments",
        "IncomeLossFromContinuingOperationsBeforeIncomeTaxesDomestic",
    ), requirement="expected"),
    Concept("income_tax", "Income tax expense", Kind.FLOW, (
        "IncomeTaxExpenseBenefit",
    ), requirement="expected"),
    Concept("interest_expense", "Interest expense", Kind.FLOW, (
        "InterestExpense", "InterestExpenseNonoperating", "InterestExpenseDebt",
        "InterestAndDebtExpense", "InterestPaidNet",
    )),
    Concept("net_income", "Net income", Kind.FLOW, (
        "NetIncomeLoss", "ProfitLoss", "NetIncomeLossAvailableToCommonStockholdersBasic",
    ), requirement="required"),
    Concept("eps_diluted", "Diluted EPS", Kind.FLOW, (
        "EarningsPerShareDiluted", "EarningsPerShareBasicAndDiluted", "EarningsPerShareBasic",
    ), unit="USD/shares", requirement="expected"),
    Concept("shares_diluted", "Diluted weighted-avg shares", Kind.FLOW, (
        "WeightedAverageNumberOfDilutedSharesOutstanding",
        "WeightedAverageNumberOfShareOutstandingBasicAndDiluted",
        "WeightedAverageNumberOfSharesOutstandingBasic",
    ), unit="shares", requirement="expected"),

    # ---- Cash-flow statement ---------------------------------------------
    Concept("cfo", "Cash from operations", Kind.FLOW, (
        "NetCashProvidedByUsedInOperatingActivities",
        "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations",
    ), statement="cashflow", requirement="expected"),
    Concept("capex", "Capital expenditures", Kind.FLOW, (
        "PaymentsToAcquirePropertyPlantAndEquipment",
        "PaymentsToAcquireProductiveAssets",
        "PaymentsForCapitalImprovements",
        "PaymentsToAcquirePropertyPlantAndEquipmentAndIntangibleAssets",
    ), statement="cashflow", requirement="expected"),
    Concept("d_and_a", "Depreciation & amortization", Kind.FLOW, (
        "DepreciationDepletionAndAmortization",
        "DepreciationAndAmortization",
        "DepreciationAmortizationAndAccretionNet",
        "Depreciation",
    ), statement="cashflow", requirement="expected"),
    Concept("dividends_paid", "Dividends paid", Kind.FLOW, (
        "PaymentsOfDividendsCommonStock", "PaymentsOfDividends",
    ), statement="cashflow"),
    Concept("buybacks", "Share repurchases", Kind.FLOW, (
        "PaymentsForRepurchaseOfCommonStock",
    ), statement="cashflow"),
    Concept("dividends_per_share", "Dividends per share", Kind.FLOW, (
        "CommonStockDividendsPerShareCashPaid", "CommonStockDividendsPerShareDeclared",
    ), unit="USD/shares", statement="cashflow"),
    Concept("gain_on_sale", "Gains on sale of property (REIT FFO add-back)", Kind.FLOW, (
        "GainLossOnSaleOfProperties", "GainsLossesOnSalesOfInvestmentRealEstate",
        "GainLossOnSaleOfPropertiesNetOfApplicableIncomeTaxes", "GainLossOnDispositionOfAssets1",
        "GainLossOnSalesOfAssetsAndAssetImpairmentCharges",
    ), statement="cashflow"),

    # ---- Balance sheet ----------------------------------------------------
    Concept("cash", "Cash & equivalents", Kind.INSTANT, (
        "CashAndCashEquivalentsAtCarryingValue",
        "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents",
        "Cash",
    ), statement="balance", requirement="expected"),
    Concept("short_term_investments", "Short-term investments", Kind.INSTANT, (
        "ShortTermInvestments", "MarketableSecuritiesCurrent",
        "AvailableForSaleSecuritiesDebtSecuritiesCurrent", "AvailableForSaleSecuritiesCurrent",
    ), statement="balance"),
    Concept("receivables", "Accounts receivable", Kind.INSTANT, (
        "AccountsReceivableNetCurrent", "ReceivablesNetCurrent",
    ), statement="balance"),
    Concept("inventory", "Inventory", Kind.INSTANT, (
        "InventoryNet", "InventoryFinishedGoodsNetOfReserves",
    ), statement="balance"),
    Concept("current_assets", "Total current assets", Kind.INSTANT, (
        "AssetsCurrent",
    ), statement="balance"),
    Concept("total_assets", "Total assets", Kind.INSTANT, (
        "Assets",
    ), statement="balance", requirement="required"),
    Concept("current_liabilities", "Total current liabilities", Kind.INSTANT, (
        "LiabilitiesCurrent",
    ), statement="balance"),
    Concept("total_liabilities", "Total liabilities", Kind.INSTANT, (
        "Liabilities",
    ), statement="balance", notes="Derived as Liabilities+Equity − Equity when not tagged directly.", requirement="expected"),
    Concept("liabilities_and_equity", "Total liabilities & equity", Kind.INSTANT, (
        "LiabilitiesAndStockholdersEquity",
    ), statement="balance", requirement="expected"),
    Concept("equity", "Shareholders' equity (book value)", Kind.INSTANT, (
        "StockholdersEquity",
        "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
    ), statement="balance", requirement="required"),
    Concept("long_term_debt", "Long-term debt", Kind.INSTANT, (
        "LongTermDebtNoncurrent", "LongTermDebt", "LongTermDebtAndCapitalLeaseObligations",
    ), statement="balance"),
    Concept("short_term_debt", "Short-term debt", Kind.INSTANT, (
        "DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings",
        "CommercialPaper", "LongTermDebtAndCapitalLeaseObligationsCurrent",
    ), statement="balance"),
    Concept("goodwill", "Goodwill", Kind.INSTANT, ("Goodwill",), statement="balance"),
    Concept("intangibles", "Intangible assets", Kind.INSTANT, (
        "IntangibleAssetsNetExcludingGoodwill", "FiniteLivedIntangibleAssetsNet",
    ), statement="balance"),
    Concept("shares_outstanding", "Shares outstanding", Kind.INSTANT, (
        "EntityCommonStockSharesOutstanding",
    ), unit="shares", taxonomy="dei", statement="balance"),
]

CONCEPTS_BY_KEY = {c.key: c for c in CONCEPTS}
