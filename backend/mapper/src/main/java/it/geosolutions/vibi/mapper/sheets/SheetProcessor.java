package it.geosolutions.vibi.mapper.sheets;

import it.geosolutions.vibi.mapper.attributes.Attribute;
import it.geosolutions.vibi.mapper.detectors.BoundsDetector;
import it.geosolutions.vibi.mapper.exceptions.VibiException;
import it.geosolutions.vibi.mapper.utils.Store;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.geotools.data.DataStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.util.List;

public final class SheetProcessor {

    private final static Logger LOGGER = Logger.getLogger(SheetProcessor.class);

    private final List<Attribute> attributes;
    private final BoundsDetector boundsDetector;
    private final SheetContextUpdater contextUpdater;

    private final SimpleFeatureType featureType;

    public SheetProcessor(String tableName, List<Attribute> attributes, BoundsDetector boundsDetector, SheetContextUpdater contextUpdater) {
        this.attributes = attributes;
        this.boundsDetector = boundsDetector;
        this.contextUpdater = contextUpdater;
        this.featureType = Store.constructFeatureType(tableName, attributes);
    }

    public void process(Sheet sheet, DataStore store) {
        LOGGER.info(String.format("Start parsing spreadsheet '%s'.", sheet.getSheetName()));
        SheetContext context = new SheetContext(sheet, store);
        Row row = findDataStartRow(context);
        while (row != null) {
            try {
                context.withRow(row);
                if (boundsDetector.dataEnd(context)) {
                    break;
                }
                contextUpdater.update(context);
                if (!boundsDetector.ignore(context)) {
                    processRow(context);
                }
                row = sheet.getRow(row.getRowNum() + 1);
            } catch (VibiException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new VibiException(exception, "Error processing row '%d' of spreadsheet '%s'.",
                        row.getRowNum() + 1, sheet.getSheetName());
            }
        }
    }

    private Row findDataStartRow(SheetContext context) {
        for (Row row : context.getSheet()) {
            if (boundsDetector.dataStart(context.withRow(row))) {
                return row;
            }
        }
        return null;
    }

    private void processRow(SheetContext context) {
        SimpleFeature feature = Store.constructFeature(featureType, context, attributes);
        Store.persistFeature(context.getStore(), feature);
    }
}
