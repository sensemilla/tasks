package org.tasks.injection;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import dagger.ObjectGraph;

public class InjectingAppCompatActivity extends AppCompatActivity implements Injector {
    private ObjectGraph objectGraph;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        objectGraph = ((Injector) getApplication()).getObjectGraph().plus(new ActivityModule(this));
        inject(this);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void inject(Object caller) {
        objectGraph.inject(caller);
    }

    @Override
    public ObjectGraph getObjectGraph() {
        return objectGraph;
    }
}
