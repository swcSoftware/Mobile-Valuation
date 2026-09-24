package com.swcsoftware.valuelens.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the display name of every company in scripts/universe.txt plus the design fixtures, as SEC
 * files them today. The expectations were reviewed by eye before being pinned (Sprint 5, owner
 * decision on ISSUES #85): a change to `CompanyNames` shows up here as a readable diff.
 */
class CompanyNamesTest {
    private val universe = listOf(
        Triple("AAPL", "Apple Inc.", "Apple Inc."),
        Triple("ABBV", "AbbVie Inc.", "AbbVie Inc."),
        Triple("ADBE", "ADOBE INC.", "Adobe Inc."),
        Triple("AGNC", "AGNC Investment Corp.", "AGNC Investment Corp."),
        Triple("ALL", "ALLSTATE CORP", "Allstate Corp"),
        Triple("AMD", "ADVANCED MICRO DEVICES INC", "Advanced Micro Devices Inc"),
        Triple("AMT", "AMERICAN TOWER CORP /MA/", "American Tower Corp"),
        Triple("AMZN", "AMAZON COM INC", "Amazon Com Inc"),
        Triple("AVGO", "Broadcom Inc.", "Broadcom Inc."),
        Triple("AXP", "AMERICAN EXPRESS CO", "American Express Co"),
        Triple("BA", "THE BOEING COMPANY", "The Boeing Company"),
        Triple("BAC", "BofA Finance LLC", "BofA Finance LLC"),
        Triple("BRK-B", "BERKSHIRE HATHAWAY INC", "Berkshire Hathaway Inc"),
        Triple("CAT", "CATERPILLAR INC", "Caterpillar Inc"),
        Triple("CMCSA", "COMCAST CORPORATION", "Comcast Corporation"),
        Triple("COP", "ConocoPhillips", "ConocoPhillips"),
        Triple("COST", "COSTCO WHOLESALE CORP /NEW", "Costco Wholesale Corp"),
        Triple("CRM", "Salesforce, Inc.", "Salesforce, Inc."),
        Triple("CRWV", "CoreWeave, Inc.", "CoreWeave, Inc."),
        Triple("CSCO", "CISCO SYSTEMS, INC.", "Cisco Systems, Inc."),
        Triple("CVX", "Chevron Corp", "Chevron Corp"),
        Triple("DE", "DEERE & CO", "Deere & Co"),
        Triple("DHI", "D.R. Horton, Inc.", "D.R. Horton, Inc."),
        Triple("DIS", "WALT DISNEY CO/", "Walt Disney Co"),
        Triple("FOXA", "Fox Corporation", "Fox Corporation"),
        Triple("GE", "GENERAL ELECTRIC CO", "General Electric Co"),
        Triple("GOOG", "Alphabet Inc.", "Alphabet Inc."),
        Triple("GOOGL", "Alphabet Inc.", "Alphabet Inc."),
        Triple("GS", "The Goldman Sachs Group, Inc.", "The Goldman Sachs Group, Inc."),
        Triple("HD", "The Home Depot, Inc.", "The Home Depot, Inc."),
        Triple("HON", "Honeywell International Inc", "Honeywell International Inc"),
        Triple("IBM", "INTERNATIONAL BUSINESS MACHINES CORP", "International Business Machines Corp"),
        Triple("INTC", "INTEL CORP", "Intel Corp"),
        Triple("JNJ", "Johnson & Johnson", "Johnson & Johnson"),
        Triple("JPM", "JPMORGAN CHASE & CO", "JPMorgan Chase & Co"),
        Triple("KO", "COCA COLA CO", "Coca Cola Co"),
        Triple("LEN", "LENNAR CORP /NEW/", "Lennar Corp"),
        Triple("LLY", "ELI LILLY AND COMPANY", "Eli Lilly and Company"),
        Triple("LMT", "LOCKHEED MARTIN CORPORATION", "Lockheed Martin Corporation"),
        Triple("MA", "Mastercard Incorporated", "Mastercard Incorporated"),
        Triple("MCD", "MCDONALDS CORP", "McDonalds Corp"),
        Triple("META", "Meta Platforms, Inc.", "Meta Platforms, Inc."),
        Triple("MMM", "3M CO", "3M Co"),
        Triple("MRK", "Merck & Co., Inc.", "Merck & Co., Inc."),
        Triple("MSFT", "MICROSOFT CORP", "Microsoft Corp"),
        Triple("MU", "Micron Technology, Inc.", "Micron Technology, Inc."),
        Triple("NFLX", "NETFLIX INC", "Netflix Inc"),
        Triple("NKE", "NIKE, Inc.", "Nike, Inc."),
        Triple("NVDA", "NVIDIA CORP", "Nvidia Corp"),
        Triple("NWSA", "NEWS CORPORATION", "News Corporation"),
        Triple("O", "REALTY INCOME CORP", "Realty Income Corp"),
        Triple("ORCL", "Oracle Corporation", "Oracle Corporation"),
        Triple("PEP", "PepsiCo, Inc.", "PepsiCo, Inc."),
        Triple("PFE", "PFIZER INC", "Pfizer Inc"),
        Triple("PG", "The Procter & Gamble Company", "The Procter & Gamble Company"),
        Triple("PGR", "PROGRESSIVE CORP/OH/", "Progressive Corp"),
        Triple("PLD", "Prologis, Inc.", "Prologis, Inc."),
        Triple("PLTR", "Palantir Technologies Inc.", "Palantir Technologies Inc."),
        Triple("PYPL", "PayPal Holdings, Inc.", "PayPal Holdings, Inc."),
        Triple("QCOM", "QUALCOMM INC/DE", "Qualcomm Inc"),
        Triple("RDDT", "Reddit, Inc.", "Reddit, Inc."),
        Triple("SBUX", "Starbucks Corporation", "Starbucks Corporation"),
        Triple("SCHW", "SCHWAB CHARLES CORP", "Schwab Charles Corp"),
        Triple("SHOP", "Shopify Inc.", "Shopify Inc."),
        Triple("SPG", "SIMON PROPERTY GROUP, INC.", "Simon Property Group, Inc."),
        Triple("T", "AT&T INC.", "AT&T Inc."),
        Triple("TSLA", "Tesla, Inc.", "Tesla, Inc."),
        Triple("TXN", "TEXAS INSTRUMENTS INCORPORATED", "Texas Instruments Incorporated"),
        Triple("UAA", "Under Armour, Inc.", "Under Armour, Inc."),
        Triple("UNH", "UnitedHealth Group Incorporated", "UnitedHealth Group Incorporated"),
        Triple("UNP", "UNION PACIFIC CORP", "Union Pacific Corp"),
        Triple("UPS", "UNITED PARCEL SERVICE INC", "United Parcel Service Inc"),
        Triple("V", "VISA INC.", "Visa Inc."),
        Triple("VZ", "VERIZON COMMUNICATIONS INC", "Verizon Communications Inc"),
        Triple("WFC", "WELLS        FARGO & COMPANY/MN", "Wells Fargo & Company"),
        Triple("WMT", "WALMART INC.", "Walmart Inc."),
        Triple("XOM", "Exxon Mobil Corporation", "Exxon Mobil Corporation"),
    )

    @Test fun everyUniverseNameDisplaysAsReviewed() {
        val wrong = universe.mapNotNull { (ticker, filed, expected) ->
            val got = CompanyNames.display(filed, ticker)
            if (got == expected) null else "$ticker: expected \"$expected\", got \"$got\""
        }
        assertEquals(emptyList(), wrong)
    }

    @Test fun theOwnersExample() {
        assertEquals("Merck & Co., Inc.", CompanyNames.display("MERCK & CO., INC.", "MRK"))
    }

    @Test fun mixedCaseNamesAreTheCompanysOwnStyling() {
        for (n in listOf("AbbVie Inc.", "CoreWeave, Inc.", "D.R. Horton, Inc.", "UnitedHealth Group Incorporated", "BofA Finance LLC"))
            assertEquals(n, CompanyNames.display(n))
    }

    @Test fun stateOfIncorporationMarkersNeverShow() {
        assertEquals("American Tower Corp", CompanyNames.display("AMERICAN TOWER CORP /MA/"))
        assertEquals("Qualcomm Inc", CompanyNames.display("QUALCOMM INC/DE"))
        assertEquals("Walt Disney Co", CompanyNames.display("WALT DISNEY CO/"))
    }

    @Test fun initialismsDigitsAndTheTickerSurvive() {
        assertEquals("AT&T Inc.", CompanyNames.display("AT&T INC.", "T"))
        assertEquals("3M Co", CompanyNames.display("3M CO", "MMM"))
        assertEquals("AGNC Investment Corp.", CompanyNames.display("AGNC INVESTMENT CORP.", "AGNC"))
        assertEquals("U.S. Bancorp", CompanyNames.display("U.S. BANCORP", "USB"))
    }

    @Test fun degenerateInputIsReturnedNotBroken() {
        assertEquals("", CompanyNames.display(""))
        assertEquals("/", CompanyNames.display(" / "), "a name that is only a marker is returned as filed, not emptied")
    }
}
