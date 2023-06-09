package plugin.inputlocation;

import sootup.core.frontend.AbstractClassSource;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.types.ClassType;
import sootup.core.views.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class JLinkInputLocation implements AnalysisInputLocation {

    private final Map<ClassType, byte[]> allClasses;
    private Collection<AbstractClassSource> classSources;

    public JLinkInputLocation(Map<ClassType, byte[]> allClasses) {
        if (allClasses.isEmpty()) {
            throw new IllegalStateException("No class content provided");
        }
        this.allClasses = allClasses;
        this.classSources = new ArrayList<>();
    }

    @Override
    public Optional<? extends AbstractClassSource> getClassSource(ClassType classType, View view) {
        AsmByteClassProvider provider = new AsmByteClassProvider(view);
        if (allClasses.containsKey(classType)) {
            return Optional.of(provider.createClassSource(this, allClasses.get(classType), classType));
        }

        return Optional.empty();
    }

    @Override
    public Collection<? extends AbstractClassSource> getClassSources(View view) {
        if (classSources.isEmpty()) {
            AsmByteClassProvider provider = new AsmByteClassProvider(view);
            for (ClassType type : allClasses.keySet()) {
                classSources.add(provider.createClassSource(this, allClasses.get(type), type));
            }
        }
        return classSources;
    }
}
