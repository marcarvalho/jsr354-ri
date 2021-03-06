package org.javamoney.moneta.internal.convert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.convert.ConversionContextBuilder;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ProviderContext;
import javax.money.convert.RateType;

import org.javamoney.moneta.ExchangeRateBuilder;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX Event Handler that reads the quotes.
 * <p>
 * Format: <gesmes:Envelope
 * xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01"
 * xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
 * <gesmes:subject>Reference rates</gesmes:subject> <gesmes:Sender>
 * <gesmes:name>European Central Bank</gesmes:name> </gesmes:Sender> <Cube>
 * <Cube time="2013-02-21">...</Cube> <Cube time="2013-02-20">...</Cube>
 * <Cube time="2013-02-19"> <Cube currency="USD" rate="1.3349"/> <Cube
 * currency="JPY" rate="124.81"/> <Cube currency="BGN" rate="1.9558"/> <Cube
 * currency="CZK" rate="25.434"/> <Cube currency="DKK" rate="7.4599"/> <Cube
 * currency="GBP" rate="0.8631"/> <Cube currency="HUF" rate="290.79"/> <Cube
 * currency="LTL" rate="3.4528"/> ...
 *
 * @author Anatole Tresch
 * @author otaviojava
 */
class RateReadingHandler extends DefaultHandler {
    /**
     * Current timestamp for the given section.
     */
    private LocalDate localDate;

    private final Map<LocalDate, Map<String, ExchangeRate>> historicRates;

    private ProviderContext context;

    public RateReadingHandler(Map<LocalDate, Map<String, ExchangeRate>> historicRates, ProviderContext context) {
        this.historicRates = historicRates;
        this.context = context;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        if ("Cube".equals(qName)) {
            if (Objects.nonNull(attributes.getValue("time"))) {

                this.localDate = LocalDate.parse(attributes.getValue("time")).atStartOfDay().toLocalDate();
            } else if (Objects.nonNull(attributes.getValue("currency"))) {
                // read data <Cube currency="USD" rate="1.3349"/>
                CurrencyUnit tgtCurrency = Monetary
                        .getCurrency(attributes.getValue("currency"));
                addRate(tgtCurrency, this.localDate, BigDecimal.valueOf(Double
                        .parseDouble(attributes.getValue("rate"))));
            }
        }
        super.startElement(uri, localName, qName, attributes);
    }

    /**
     * Method to add a currency exchange rate.
     *
     * @param term      the term (target) currency, mapped from EUR.
     * @param timestamp The target day.
     * @param rate      The rate.
     */
    void addRate(CurrencyUnit term, LocalDate localDate, Number rate) {
        RateType rateType = RateType.HISTORIC;
        ExchangeRateBuilder builder;
        if (Objects.nonNull(localDate)) {
            // TODO check/test!
            if (localDate.equals(LocalDate.now())) {
                rateType = RateType.DEFERRED;
            }
            builder = new ExchangeRateBuilder(
                    ConversionContextBuilder.create(context, rateType).set(localDate).build());
        } else {
            builder = new ExchangeRateBuilder(ConversionContextBuilder.create(context, rateType).build());
        }
        builder.setBase(ECBHistoricRateProvider.BASE_CURRENCY);
        builder.setTerm(term);
        builder.setFactor(DefaultNumberValue.of(rate));
        ExchangeRate exchangeRate = builder.build();
        Map<String, ExchangeRate> rateMap = this.historicRates.get(localDate);
        if (Objects.isNull(rateMap)) {
            synchronized (this.historicRates) {
                rateMap = Optional.ofNullable(this.historicRates.get(localDate)).orElse(new ConcurrentHashMap<>());
                this.historicRates.putIfAbsent(localDate, rateMap);

            }
        }
        rateMap.put(term.getCurrencyCode(), exchangeRate);
    }

}