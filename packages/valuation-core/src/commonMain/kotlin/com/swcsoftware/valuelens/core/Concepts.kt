package com.swcsoftware.valuelens.core

/**
 * Canonical concept → ordered XBRL tags to try. Mirrors services/valuation-engine normalize/tags.py.
 * The first tag with data for a period wins; the chosen tag is recorded on every value.
 */
enum class Kind { FLOW, INSTANT }

class Concept(
    val key: String, val label: String, val kind: Kind, val tags: List<String>,
    val unit: String = "USD", val taxonomy: String = "us-gaap", val statement: String = "income",
)

object Concepts {
    val ALL: List<Concept> = listOf(
        Concept("revenue", "Revenue", Kind.FLOW, listOf("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax", "SalesRevenueNet", "RevenueFromContractWithCustomerIncludingAssessedTax", "RevenuesNetOfInterestExpense")),
        Concept("cost_of_revenue", "Cost of revenue", Kind.FLOW, listOf("CostOfRevenue", "CostOfGoodsAndServicesSold", "CostOfGoodsSold")),
        Concept("operating_income", "Operating income (EBIT)", Kind.FLOW, listOf("OperatingIncomeLoss")),
        Concept("pretax_income", "Pre-tax income", Kind.FLOW, listOf("IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest", "IncomeLossFromContinuingOperationsBeforeIncomeTaxesMinorityInterestAndIncomeLossFromEquityMethodInvestments", "IncomeLossFromContinuingOperationsBeforeIncomeTaxesDomestic")),
        Concept("income_tax", "Income tax expense", Kind.FLOW, listOf("IncomeTaxExpenseBenefit")),
        Concept("interest_expense", "Interest expense", Kind.FLOW, listOf("InterestExpense", "InterestExpenseNonoperating", "InterestExpenseDebt", "InterestAndDebtExpense", "InterestPaidNet")),
        Concept("net_income", "Net income", Kind.FLOW, listOf("NetIncomeLoss", "ProfitLoss", "NetIncomeLossAvailableToCommonStockholdersBasic")),
        Concept("eps_diluted", "Diluted EPS", Kind.FLOW, listOf("EarningsPerShareDiluted", "EarningsPerShareBasicAndDiluted", "EarningsPerShareBasic"), unit = "USD/shares"),
        Concept("shares_diluted", "Diluted weighted-avg shares", Kind.FLOW, listOf("WeightedAverageNumberOfDilutedSharesOutstanding", "WeightedAverageNumberOfShareOutstandingBasicAndDiluted", "WeightedAverageNumberOfSharesOutstandingBasic"), unit = "shares"),
        Concept("cfo", "Cash from operations", Kind.FLOW, listOf("NetCashProvidedByUsedInOperatingActivities", "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"), statement = "cashflow"),
        Concept("capex", "Capital expenditures", Kind.FLOW, listOf("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets", "PaymentsForCapitalImprovements", "PaymentsToAcquirePropertyPlantAndEquipmentAndIntangibleAssets"), statement = "cashflow"),
        Concept("d_and_a", "Depreciation & amortization", Kind.FLOW, listOf("DepreciationDepletionAndAmortization", "DepreciationAndAmortization", "DepreciationAmortizationAndAccretionNet", "Depreciation"), statement = "cashflow"),
        Concept("dividends_paid", "Dividends paid", Kind.FLOW, listOf("PaymentsOfDividendsCommonStock", "PaymentsOfDividends"), statement = "cashflow"),
        Concept("buybacks", "Share repurchases", Kind.FLOW, listOf("PaymentsForRepurchaseOfCommonStock"), statement = "cashflow"),
        Concept("cash", "Cash & equivalents", Kind.INSTANT, listOf("CashAndCashEquivalentsAtCarryingValue", "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents", "Cash"), statement = "balance"),
        Concept("short_term_investments", "Short-term investments", Kind.INSTANT, listOf("ShortTermInvestments", "MarketableSecuritiesCurrent", "AvailableForSaleSecuritiesDebtSecuritiesCurrent", "AvailableForSaleSecuritiesCurrent"), statement = "balance"),
        Concept("receivables", "Accounts receivable", Kind.INSTANT, listOf("AccountsReceivableNetCurrent", "ReceivablesNetCurrent"), statement = "balance"),
        Concept("inventory", "Inventory", Kind.INSTANT, listOf("InventoryNet", "InventoryFinishedGoodsNetOfReserves"), statement = "balance"),
        Concept("current_assets", "Total current assets", Kind.INSTANT, listOf("AssetsCurrent"), statement = "balance"),
        Concept("total_assets", "Total assets", Kind.INSTANT, listOf("Assets"), statement = "balance"),
        Concept("current_liabilities", "Total current liabilities", Kind.INSTANT, listOf("LiabilitiesCurrent"), statement = "balance"),
        Concept("total_liabilities", "Total liabilities", Kind.INSTANT, listOf("Liabilities"), statement = "balance"),
        Concept("liabilities_and_equity", "Total liabilities & equity", Kind.INSTANT, listOf("LiabilitiesAndStockholdersEquity"), statement = "balance"),
        Concept("equity", "Shareholders' equity (book value)", Kind.INSTANT, listOf("StockholdersEquity", "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"), statement = "balance"),
        Concept("long_term_debt", "Long-term debt", Kind.INSTANT, listOf("LongTermDebtNoncurrent", "LongTermDebt", "LongTermDebtAndCapitalLeaseObligations"), statement = "balance"),
        Concept("short_term_debt", "Short-term debt", Kind.INSTANT, listOf("DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings", "CommercialPaper", "LongTermDebtAndCapitalLeaseObligationsCurrent"), statement = "balance"),
        Concept("goodwill", "Goodwill", Kind.INSTANT, listOf("Goodwill"), statement = "balance"),
        Concept("intangibles", "Intangible assets", Kind.INSTANT, listOf("IntangibleAssetsNetExcludingGoodwill", "FiniteLivedIntangibleAssetsNet"), statement = "balance"),
        Concept("shares_outstanding", "Shares outstanding", Kind.INSTANT, listOf("EntityCommonStockSharesOutstanding"), unit = "shares", taxonomy = "dei", statement = "balance"),
    )
    val BY_KEY: Map<String, Concept> = ALL.associateBy { it.key }
}
