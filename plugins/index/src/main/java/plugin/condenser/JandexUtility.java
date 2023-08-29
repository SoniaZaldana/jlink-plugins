package plugin.condenser;

import org.glavo.classfile.ClassElement;
import org.glavo.classfile.ClassModel;
import org.glavo.classfile.Classfile;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.stream.Stream;

public class JandexUtility {

    public static void persistIndexToDisk(String fileName, Model model) throws Exception {

        List<ClassModel> classModels = Stream.concat(model.modules(), model.classPath())
                .flatMap(model::containerClasses)
                .map(model::classContents)
                .map(ClassContents::classModel) // class entries get mapped to ClassModel (defined by the new java.lang.classfile API)
                .toList();

        Indexer indexer = new Indexer();

        for (ClassModel classModel : classModels) {
            // Unsure how else to get byte array from Class Model without transforming it?
            byte[] newBytes = Classfile.build(classModel.thisClass().asSymbol(),
                    classBuilder -> {
                        for (ClassElement ce : classModel) {
                            classBuilder.with(ce);
                        }
                    });

            indexer.index(new ByteArrayInputStream(newBytes));
        }
        Index index = indexer.complete();

        // persist to disk
        FileOutputStream out = new FileOutputStream(fileName);
        IndexWriter writer = new IndexWriter(out);
        writer.write(index);

    }

    public static Index loadIndexFromDisk(String fileName) throws Exception {
        FileInputStream input = new FileInputStream(fileName);
        IndexReader reader = new IndexReader(input);
        Index index = reader.read();
        return index;
    }
}
