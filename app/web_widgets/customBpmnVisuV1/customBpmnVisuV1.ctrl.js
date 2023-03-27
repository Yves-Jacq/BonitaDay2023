function ctrl($scope, uiTranslateFilter) {

    const allElementTypes = Object.values(bpmnvisu.ShapeBpmnElementKind);
    const flowNodesStates = new Map();

    let bpmnContainerElt;
    let bpmnVisualization;

    $scope.featureIsAvailable = false;
    $scope.$watch('properties.activeFeatures', () => {
        $scope.featureIsAvailable = isFeatureAvailable();
        if ($scope.featureIsAvailable) {
            bpmnContainerElt = window.document.getElementById('bpmn-container');
            bpmnVisualization = new BpmnVisualizationCustomColors({ container: 'bpmn-container', navigation: { enabled: true } });

            // Show diagram if we find one
            showDiagram();
        }
    });

    function showDiagram() {
        $scope.$watchGroup(['properties.diagram', 'properties.processName'], () => {
            if ($scope.properties.diagram && $scope.properties.processName) {
                bpmnVisualization.load($scope.properties.diagram,
                    { fit: {type: bpmnvisu.FitType.Center}, modelFilter: {pools: [{name: $scope.properties.processName}]} });

                // Get all elements in the diagram that may need a label or popover
                const allElements = getElementsToDecorateInDiagram();

                // Show labels on diagram elements
                showLabels(allElements);

                // Add popover
                addPopover(allElements);
            }
        });
    }

    this.fitCenter = function () {
        bpmnVisualization.navigation.fit({ type: bpmnvisu.FitType.Center });
    }

    this.zoomIn = function () {
        bpmnVisualization.navigation.zoom(bpmnvisu.ZoomType.In);
    }

    this.zoomOut = function () {
        bpmnVisualization.navigation.zoom(bpmnvisu.ZoomType.Out);
    }

    function getElementsToDecorateInDiagram() {
        // Remove events and gateways since we don't want to show counters on them
        const taskTypes = allElementTypes.filter(
            type => !type.endsWith('Event') && 
            !type.endsWith('Gateway')
        );
        
        if(taskTypes) {
            return bpmnVisualization.bpmnElementsRegistry.getElementsByKinds(taskTypes);   
        }
    }

    function showLabels(elements) {
        $scope.$watch('properties.flowNodesInfo', () => {
            let states = $scope.properties.flowNodesInfo && $scope.properties.flowNodesInfo.flowNodeStatesCounters;
            if (states) {
                let flowNodes = Object.keys(states);
                let flowNodesStatesValues = Object.values(states);
                flowNodes.forEach((flowNodeName, index) => {
                    // Find current flowNode in diagram
                    const diagramElement = getDiagramElement(flowNodeName, elements);
                    if (diagramElement) {
                        const flowNodeId = diagramElement.bpmnSemantic.id
                        // Get values of current flowNode
                        let flowNodeStates = new FlowNodeStates(flowNodesStatesValues[index]);
                        flowNodesStates.set(flowNodeName, flowNodeStates);
                        if (flowNodeStates.failed) {
                            bpmnVisualization.bpmnElementsRegistry.addOverlays(flowNodeId, {
                                position: 'top-right',
                                label: (flowNodeStates.failed).toString(),
                                style: {
                                    font: {color: 'White'},
                                    fill: {color: 'Red'},
                                    stroke: {color: 'Red'}
                                }
                            });
                        }
                        if (flowNodeStates.executing) {
                            bpmnVisualization.bpmnElementsRegistry.addOverlays(flowNodeId, {
                                position: 'middle-right',
                                label: (flowNodeStates.executing).toString(),
                                style: {
                                    font: {color: 'White'},
                                    fill: {color: 'Blue'},
                                    stroke: {color: 'Blue'}
                                }
                            });
                        }
                        if (flowNodeStates.readyWaiting) {
                            bpmnVisualization.bpmnElementsRegistry.addOverlays(flowNodeId, {
                                position: 'bottom-right',
                                label: (flowNodeStates.readyWaiting).toString(),
                                style: {
                                    font: {color: 'White'},
                                    fill: {color: 'Green'},
                                    stroke: {color: 'Green'}
                                }
                            });
                        }
                        if (flowNodeStates.completed) {
                            bpmnVisualization.bpmnElementsRegistry.addOverlays(flowNodeId, {
                                position: 'bottom-left',
                                label: (flowNodeStates.completed).toString(),
                                style: {
                                    font: {color: 'White'},
                                    fill: {color: 'Black'},
                                    stroke: {color: 'Black'}
                                }
                            });
                        }
                        if (flowNodeStates.cancelledSkipped) {
                            bpmnVisualization.bpmnElementsRegistry.addOverlays(flowNodeId, {
                                position: 'top-left',
                                label: (flowNodeStates.cancelledSkipped).toString(),
                                style: {
                                    font: {color: 'White'},
                                    fill: {color: 'Grey'},
                                    stroke: {color: 'Grey'}
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    function getDiagramElement(flowNodeName, diagramElements) {
        return diagramElements.find((element) => element.bpmnSemantic.name === flowNodeName);
    }

    function getPopoverContent(flowNodeStates) {
        let nbFailed = flowNodeStates.failed;
        let failedMessage = getStateMessage(nbFailed, translate('in error'));
        let nbExecuting = flowNodeStates.executing;
        let executingMessage = getStateMessage(nbExecuting, translate('executing'));
        let nbReadyWaiting = flowNodeStates.readyWaiting;
        let readyWaitingMessage = getStateMessage(nbReadyWaiting, translate('waiting'));
        let nbCompleted = flowNodeStates.completed;
        let completedMessage = getStateMessage(nbCompleted, translate('completed'));
        let nbCancelledSkipped = flowNodeStates.cancelledSkipped;
        let cancelledSkippedMessage = getStateMessage(nbCancelledSkipped, translate('cancelled or skipped'));

        let content = nbFailed ? `<div class='box red'></div>${failedMessage}<br>` : "";
        content += nbExecuting ? `<div class='box blue'></div>${executingMessage}<br>` : "";
        content += nbReadyWaiting ? `<div class='box green'></div>${readyWaitingMessage}<br>` : "";
        content += nbCompleted ? `<div class='box black'></div>${completedMessage}<br>` : "";
        content += nbCancelledSkipped ? `<div class='box grey'></div>${cancelledSkippedMessage}<br>` : "";
        return content;
    }

    function getStateMessage(nb, trailingMessage) {
        if (nb) {
            if (nb === 1) {
                return `&nbsp; ${nb}&nbsp; ${translate(`instance is`)}&nbsp;${trailingMessage}`;
            } else {
                return `&nbsp; ${nb}&nbsp; ${translate(`instances are`)}&nbsp;${trailingMessage}`;
            }
        }
        return "";
    }

    function translate(item) {
        return uiTranslateFilter(item);
    }

    function addPopover(bpmnElements) {
        bpmnElements.forEach(bpmnElement => {
            const htmlElement = bpmnElement.htmlElement;
            const isEdge = !bpmnElement.bpmnSemantic.isShape;
            const offset = isEdge? [0, -40] : undefined; // undefined offset for tippyjs default offset

            tippy(htmlElement, {
                onShow(instance) {
                    let flowNodeStates = flowNodesStates.get(bpmnElement.bpmnSemantic.name);
                    if (flowNodeStates) {
                        instance.setContent(getPopoverContent(flowNodeStates));
                    } else {
                        // Hide the popover if no content
                        instance.popper.hidden = true;
                    }
                },
                // work perfectly on hover with or without 'diagram navigation' enable
                appendTo: bpmnContainerElt.parentElement,

                // https://atomiks.github.io/tippyjs/v6/all-props/#sticky
                // This has a performance cost since checks are run on every animation frame. Use this only when necessary!
                // only check the "reference" rect for changes
                sticky: 'reference',

                arrow: true,
                offset: offset,
                placement: 'top',
                maxWidth: 'none',
                allowHTML: true
            });
        });
    }

    function isFeatureAvailable() {
        if ($scope.properties.activeFeatures) {
            for (let feature of $scope.properties.activeFeatures) {
                if (feature.name === 'PROCESS_MONITORING') {
                    return true;
                }
            }
        }
        return false;
    }

    class FlowNodeStates {
        constructor(flowNodeStates) {
            this.failed = (flowNodeStates.failed ? flowNodeStates.failed : 0);

            // Executing
            this.executing = flowNodeStates.executing ? flowNodeStates.executing : 0;

            // ReadyWaiting
            this.ready = flowNodeStates.ready ? flowNodeStates.ready : 0;
            this.waiting = flowNodeStates.waiting ? flowNodeStates.waiting : 0;
            this.readyWaiting = this.ready + this.waiting;

            this.completed = flowNodeStates.completed ? flowNodeStates.completed : 0;

            // CanceledSkipped
            this.cancelled = flowNodeStates.cancelled ? flowNodeStates.cancelled : 0;
            this.skipped = flowNodeStates.skipped ? flowNodeStates.skipped : 0;
            this.cancelledSkipped = this.cancelled + this.skipped;
        }
    }

    class BpmnVisualizationCustomColors extends bpmnvisu.BpmnVisualization {

        constructor(options) {
            super(options);
            this.configureStyle();
        }

        configureStyle() {
            const styleSheet = this.graph.getStylesheet(); // mxStylesheet

            const startEventStyle = styleSheet.styles[bpmnvisu.ShapeBpmnElementKind.EVENT_START];
            startEventStyle[StyleIdentifiers.STYLE_GRADIENT_DIRECTION] = Directions.DIRECTION_WEST;
            startEventStyle[StyleIdentifiers.STYLE_GRADIENTCOLOR] = '#FBFBEE';
            startEventStyle[StyleIdentifiers.STYLE_FILLCOLOR] = '#E9ECB1';
            startEventStyle[StyleIdentifiers.STYLE_STROKECOLOR] = '#62A928';

            [bpmnvisu.ShapeBpmnElementKind.EVENT_INTERMEDIATE_CATCH, bpmnvisu.ShapeBpmnElementKind.EVENT_INTERMEDIATE_THROW].forEach(kind => {
                const intermediateEventStyle = styleSheet.styles[kind];
                intermediateEventStyle[StyleIdentifiers.STYLE_STROKECOLOR] = '#2E6DA3';
                intermediateEventStyle[StyleIdentifiers.STYLE_FILLCOLOR] = '#ffffff'; // ensure reset if the style is redefined before
            })

            const boundaryEventStyle = styleSheet.styles[bpmnvisu.ShapeBpmnElementKind.EVENT_BOUNDARY];
            boundaryEventStyle[StyleIdentifiers.STYLE_FILLCOLOR] = '#ffffff';
            boundaryEventStyle[StyleIdentifiers.STYLE_STROKECOLOR] = '#2E6DA3';

            const endEventStyle = styleSheet.styles[bpmnvisu.ShapeBpmnElementKind.EVENT_END];
            endEventStyle[StyleIdentifiers.STYLE_GRADIENT_DIRECTION] = Directions.DIRECTION_WEST;
            endEventStyle[StyleIdentifiers.STYLE_GRADIENTCOLOR] = '#FFFFFF';
            endEventStyle[StyleIdentifiers.STYLE_FILLCOLOR] = '#F9D0C6';
            endEventStyle[StyleIdentifiers.STYLE_STROKECOLOR] = '#89151A';

            bpmnvisu.ShapeUtil.taskKinds().forEach(kind => {
                const style = styleSheet.styles[kind];
                style[StyleIdentifiers.STYLE_GRADIENT_DIRECTION] = Directions.DIRECTION_WEST;
                style[StyleIdentifiers.STYLE_GRADIENTCOLOR] = '#FBFBEE';
                style[StyleIdentifiers.STYLE_FILLCOLOR] = '#E5E6F1';
                style[StyleIdentifiers.STYLE_STROKECOLOR] = '#2C6DA3';
            });

            bpmnvisu.ShapeUtil.gatewayKinds().forEach(kind => {
                const style = styleSheet.styles[kind];
                style[StyleIdentifiers.STYLE_GRADIENT_DIRECTION] = Directions.DIRECTION_WEST;
                style[StyleIdentifiers.STYLE_GRADIENTCOLOR] = '#FBFBEE';
                style[StyleIdentifiers.STYLE_FILLCOLOR] = '#E9ECB1';
                style[StyleIdentifiers.STYLE_STROKECOLOR] = '#96A826';
            });
         }
    }

    class StyleIdentifiers {
        static STYLE_FILLCOLOR = 'fillColor';
        static STYLE_STROKECOLOR = 'strokeColor';

        static STYLE_GRADIENT_DIRECTION = 'gradientDirection';
        static STYLE_GRADIENTCOLOR = 'gradientColor';

        static STYLE_FONTCOLOR = 'fontColor';
        static STYLE_FONTFAMILY = 'fontFamily';
        static STYLE_FONTSIZE = 'fontSize';
        static STYLE_FONTSTYLE = 'fontStyle';

        static STYLE_SWIMLANE_FILLCOLOR = 'swimlaneFillColor';
    }

    class Directions {
        static DIRECTION_EAST = 'east';
        static DIRECTION_SOUTH = 'south';
        static DIRECTION_WEST = 'west';
    }

    class FontStyle {
        static FONT_BOLD = 1;
        static FONT_ITALIC = 2;
    }

}
