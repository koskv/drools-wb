/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.drools.workbench.verifier.webworker.client;

import java.util.Set;

import org.drools.workbench.services.verifier.api.client.Status;
import org.drools.workbench.services.verifier.api.client.configuration.AnalyzerConfiguration;
import org.drools.workbench.services.verifier.api.client.configuration.RunnerType;
import org.drools.workbench.services.verifier.api.client.index.Index;
import org.drools.workbench.services.verifier.api.client.reporting.Issue;
import org.drools.workbench.services.verifier.api.client.reporting.Issues;
import org.drools.workbench.services.verifier.core.main.Analyzer;
import org.drools.workbench.services.verifier.core.main.Reporter;
import org.drools.workbench.services.verifier.plugin.client.api.DeleteColumns;
import org.drools.workbench.services.verifier.plugin.client.api.Initialize;
import org.drools.workbench.services.verifier.plugin.client.api.MakeRule;
import org.drools.workbench.services.verifier.plugin.client.api.NewColumn;
import org.drools.workbench.services.verifier.plugin.client.api.RemoveRule;
import org.drools.workbench.services.verifier.plugin.client.api.RequestStatus;
import org.drools.workbench.services.verifier.plugin.client.api.Update;
import org.drools.workbench.services.verifier.plugin.client.api.WebWorkerException;
import org.drools.workbench.services.verifier.plugin.client.builders.BuildException;
import org.uberfire.commons.validation.PortablePreconditions;

public class Receiver {

    private Analyzer analyzer;
    private Poster poster;
    private RunnerType runnerType;
    private Issues latestReport;
    private Index index;
    private AnalyzerConfiguration configuration;

    public Receiver( final Poster poster,
                     final RunnerType runnerType ) {
        this.poster = PortablePreconditions.checkNotNull( "poster",
                                                          poster );
        this.runnerType = PortablePreconditions.checkNotNull( "runnerType",
                                                              runnerType );
    }

    public void received( final Object o ) {
        if ( o instanceof Initialize ) {
            init( (Initialize) o );
        } else if ( o instanceof RequestStatus ) {
            requestStatus();
        } else if ( o instanceof RemoveRule ) {
            removeRule( (RemoveRule) o );
        } else if ( o instanceof Update ) {
            update( (Update) o );
        } else if ( o instanceof DeleteColumns ) {
            deleteColumns( (DeleteColumns) o );
        } else if ( o instanceof MakeRule ) {
            makeRule( (MakeRule) o );
        } else if ( o instanceof NewColumn ) {
            newColumn( (NewColumn) o );
        }
    }

    private void removeRule( final RemoveRule removeRule ) {
        try {
            getUpdateManager().removeRule( removeRule.getDeletedRow() );
        } catch ( final Exception e ) {
            poster.post( new WebWorkerException( "Failed to remove a rule: " +
                                                         e.getMessage() ) );
        }
    }

    private void deleteColumns( final DeleteColumns deleteColumns ) {
        try {
            getUpdateManager().deleteColumns( deleteColumns.getFirstColumnIndex(),
                                              deleteColumns.getNumberOfColumns() );
        } catch ( final Exception e ) {
            poster.post( new WebWorkerException( "Deleting columns failed: " +
                                                         e.getMessage() ) );
        }

    }

    private void update( final Update update ) {
        try {
            getUpdateManager().update( update.getModel(),
                                       update.getCoordinates() );
        } catch ( final UpdateException e ) {
            poster.post( new WebWorkerException( "Dtable update failed: " +
                                                         e.getMessage() ) );
        }
    }

    private void requestStatus() {
        if ( latestReport != null ) {
            poster.post( latestReport );
        }
    }

    private void newColumn( final NewColumn newColumn ) {
        try {
            getUpdateManager().newColumn( newColumn.getModel(),
                                          newColumn.getHeaderMetaData(),
                                          newColumn.getFactTypes(),
                                          newColumn.getColumnIndex() );
        } catch ( final BuildException buildException ) {
            poster.post( new WebWorkerException( "Adding a new column failed: " +
                                                         buildException.getMessage() ) );
        }
    }

    private void makeRule( final MakeRule makeRule ) {
        try {
            getUpdateManager().makeRule( makeRule.getModel(),
                                         makeRule.getHeaderMetaData(),
                                         makeRule.getFactTypes(),
                                         makeRule.getIndex() );
        } catch ( final BuildException buildException ) {
            poster.post( new WebWorkerException( "Rule Creation failed: " +
                                                         buildException.getMessage() ) );
        }
    }

    private DTableUpdateManager getUpdateManager() {
        return new DTableUpdateManager( index,
                                        analyzer,
                                        configuration );
    }

    private void init( final Initialize initialize ) {
        try {
            final AnalyzerBuilder analyzerBuilder = new AnalyzerBuilder()
                    .with( initialize )
                    .with(runnerType)
                    .with( new Reporter() {
                        @Override
                        public void sendReport( final Set<Issue> issues ) {
                            latestReport = new Issues( initialize.getUuid(),
                                                       issues );
                            poster.post( latestReport );
                        }

                        @Override
                        public void sendStatus( final Status status ) {
                            poster.post( status );
                        }
                    } );


            analyzer = analyzerBuilder.buildAnalyzer();
            index = analyzerBuilder.getIndex();
            configuration = analyzerBuilder.getConfiguration();

            analyzer.resetChecks();
            analyzer.analyze();

        } catch ( final BuildException e ) {
            poster.post( new WebWorkerException( "Initialization failed: " +
                                                         e.getMessage() ) );
        }
    }
}
