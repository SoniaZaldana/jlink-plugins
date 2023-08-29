package plugin.condenser;

import java.util.stream.Stream;

import org.glavo.classfile.ClassModel;

public interface Model {
    Stream<EntityKey.ContainerKey> modules();
    Stream<EntityKey.ContainerKey> classPath();
    Stream<EntityKey.ClassKey> containerClasses(EntityKey.ContainerKey containerKey);
    Stream<EntityKey.ResourceKey> containerResources(EntityKey.ContainerKey containerKey);
    EntityKey.ContainerKind containerKind(EntityKey.ContainerKey containerKey);
    ClassContents classContents(EntityKey.ClassKey classKey);
    ModelUpdater updater();
    Model apply(ModelUpdater updater);

}

interface ClassContents {
    ClassModel classModel();
}


