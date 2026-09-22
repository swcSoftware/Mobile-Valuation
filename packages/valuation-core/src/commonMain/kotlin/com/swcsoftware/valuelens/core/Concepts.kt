package com.swcsoftware.valuelens.core

/**
 * Canonical concept → ordered XBRL tags to try. Mirrors services/valuation-engine normalize/tags.py.
 * The first tag with data for a period wins; the chosen tag is recorded on every value.
 */
enum class Kind { FLOW, INSTANT }

class Concept(
    val key: String, val label: String, val kind: Kind, val tags: List<String>,
    val unit: String = "USD", val taxonomy: String = "us-gaap", val statement: String = "income",
    /**
     * How much its absence matters (coverage probe + "missing concepts" warning):
     * required — every operating company reports it; expected — most do, absence disables a model
     * input; optional — legitimately absent for many filers (no inventory, dividend, property sales).
     */
    val requirement: String = "optional",
)

object Concepts {
    /** Bump when the tag map changes; reported with coverage gaps so a report names the map it came from. */
    const val VERSION = "2026-09-22"

    val ALL: List<Concept> = listOf(
        Concept("revenue", "Revenue", Kind.FLOW, listOf("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax", "SalesRevenueNet", "RevenueFromContractWithCustomerIncludingAssessedTax", "RevenuesNetOfInterestExpense"), requirement = "required"),
        Concept("cost_of_revenue", "Cost of revenue", Kind.FLOW, listOf("CostOfRevenue", "CostOfGoodsAndServicesSold", "CostOfGoodsSold")),
        Concept("operating_income", "Operating income (EBIT)", Kind.FLOW, listOf("OperatingIncomeLoss"), requirement = "expected"),
        Concept("pretax_income", "Pre-tax income", Kind.FLOW, listOf("IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest", "IncomeLossFromContinuingOperationsBeforeIncomeTaxesMinorityInterestAndIncomeLossFromEquityMethodInvestments", "IncomeLossFromContinuingOperationsBeforeIncomeTaxesDomestic"), requirement = "expected"),
        Concept("income_tax", "Income tax expense", Kind.FLOW, listOf("IncomeTaxExpenseBenefit"), requirement = "expected"),
        Concept("interest_expense", "Interest expense", Kind.FLOW, listOf("InterestExpense", "InterestExpenseNonoperating", "InterestExpenseDebt", "InterestAndDebtExpense", "InterestPaidNet")),
        Concept("net_income", "Net income", Kind.FLOW, listOf("NetIncomeLoss", "ProfitLoss", "NetIncomeLossAvailableToCommonStockholdersBasic"), requirement = "required"),
        Concept("eps_diluted", "Diluted EPS", Kind.FLOW, listOf("EarningsPerShareDiluted", "EarningsPerShareBasicAndDiluted", "EarningsPerShareBasic"), unit = "USD/shares", requirement = "expected"),
        Concept("shares_diluted", "Diluted weighted-avg shares", Kind.FLOW, listOf("WeightedAverageNumberOfDilutedSharesOutstanding", "WeightedAverageNumberOfShareOutstandingBasicAndDiluted", "WeightedAverageNumberOfSharesOutstandingBasic"), unit = "shares", requirement = "expected"),
        Concept("cfo", "Cash from operations", Kind.FLOW, listOf("NetCashProvidedByUsedInOperatingActivities", "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"), statement = "cashflow", requirement = "expected"),
        Concept("capex", "Capital expenditures", Kind.FLOW, listOf("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets", "PaymentsForCapitalImprovements", "PaymentsToAcquirePropertyPlantAndEquipmentAndIntangibleAssets"), statement = "cashflow", requirement = "expected"),
        Concept("d_and_a", "Depreciation & amortization", Kind.FLOW, listOf("DepreciationDepletionAndAmortization", "DepreciationAndAmortization", "DepreciationAmortizationAndAccretionNet", "Depreciation"), statement = "cashflow", requirement = "expected"),
        Concept("dividends_paid", "Dividends paid", Kind.FLOW, listOf("PaymentsOfDividendsCommonStock", "PaymentsOfDividends"), statement = "cashflow"),
        Concept("buybacks", "Share repurchases", Kind.FLOW, listOf("PaymentsForRepurchaseOfCommonStock"), statement = "cashflow"),
        Concept("dividends_per_share", "Dividends per share", Kind.FLOW, listOf("CommonStockDividendsPerShareCashPaid", "CommonStockDividendsPerShareDeclared"), unit = "USD/shares", statement = "cashflow"),
        Concept("gain_on_sale", "Gains on sale of property (REIT FFO add-back)", Kind.FLOW, listOf("GainLossOnSaleOfProperties", "GainsLossesOnSalesOfInvestmentRealEstate", "GainLossOnSaleOfPropertiesNetOfApplicableIncomeTaxes", "GainLossOnDispositionOfAssets1", "GainLossOnSalesOfAssetsAndAssetImpairmentCharges"), statement = "cashflow"),
        Concept("cash", "Cash & equivalents", Kind.INSTANT, listOf("CashAndCashEquivalentsAtCarryingValue", "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents", "Cash"), statement = "balance", requirement = "expected"),
        Concept("short_term_investments", "Short-term investments", Kind.INSTANT, listOf("ShortTermInvestments", "MarketableSecuritiesCurrent", "AvailableForSaleSecuritiesDebtSecuritiesCurrent", "AvailableForSaleSecuritiesCurrent"), statement = "balance"),
        Concept("receivables", "Accounts receivable", Kind.INSTANT, listOf("AccountsReceivableNetCurrent", "ReceivablesNetCurrent"), statement = "balance"),
        Concept("inventory", "Inventory", Kind.INSTANT, listOf("InventoryNet", "InventoryFinishedGoodsNetOfReserves"), statement = "balance"),
        Concept("current_assets", "Total current assets", Kind.INSTANT, listOf("AssetsCurrent"), statement = "balance"),
        Concept("total_assets", "Total assets", Kind.INSTANT, listOf("Assets"), statement = "balance", requirement = "required"),
        Concept("current_liabilities", "Total current liabilities", Kind.INSTANT, listOf("LiabilitiesCurrent"), statement = "balance"),
        Concept("total_liabilities", "Total liabilities", Kind.INSTANT, listOf("Liabilities"), statement = "balance", requirement = "expected"),
        Concept("liabilities_and_equity", "Total liabilities & equity", Kind.INSTANT, listOf("LiabilitiesAndStockholdersEquity"), statement = "balance", requirement = "expected"),
        Concept("equity", "Shareholders' equity (book value)", Kind.INSTANT, listOf("StockholdersEquity", "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"), statement = "balance", requirement = "required"),
        Concept("long_term_debt", "Long-term debt", Kind.INSTANT, listOf("LongTermDebtNoncurrent", "LongTermDebt", "LongTermDebtAndCapitalLeaseObligations"), statement = "balance"),
        Concept("short_term_debt", "Short-term debt", Kind.INSTANT, listOf("DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings", "CommercialPaper", "LongTermDebtAndCapitalLeaseObligationsCurrent"), statement = "balance"),
        Concept("goodwill", "Goodwill", Kind.INSTANT, listOf("Goodwill"), statement = "balance"),
        Concept("intangibles", "Intangible assets", Kind.INSTANT, listOf("IntangibleAssetsNetExcludingGoodwill", "FiniteLivedIntangibleAssetsNet"), statement = "balance"),
        Concept("shares_outstanding", "Shares outstanding", Kind.INSTANT, listOf("EntityCommonStockSharesOutstanding"), unit = "shares", taxonomy = "dei", statement = "balance"),
    )
    val BY_KEY: Map<String, Concept> = ALL.associateBy { it.key }
}
