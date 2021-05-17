package org.processmining.plugins.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.util.collection.AlphanumComparator;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;

import com.fluxicon.slickerbox.components.NiceSlider;
import com.fluxicon.slickerbox.components.RoundedPanel;
import com.fluxicon.slickerbox.ui.SlickerScrollBarUI;

import info.clearthought.layout.TableLayoutConstants;

public class TSDecomposeByCancellationsUI {

	private final UIPluginContext context;

	private TransitionSystem regularTransitionSystem;

	private TransitionSystem specialTransitionSystem;

	public TSDecomposeByCancellationsUI(UIPluginContext context) {
		this.context = context;
	}

	/**
	 * Decompose given transition system
	 * 
	 * @param transitionSystem
	 * @return
	 */
	public List<Object> decompose(TransitionSystem transitionSystem) {

		StateSplitStep myStep = new StateSplitStep(transitionSystem);

		myStep.initComponents();
		myStep.repaint();	
		context.showWizard("Decompose TS", true, true, myStep);
		
		return myStep.retrieveSpecialStates();
	}

	//-----------------------GRAPHICS
	
	protected Color colorBg = new Color(140, 140, 140);
	protected Color colorOuterBg = new Color(100, 100, 100);
	protected Color colorListBg = new Color(60, 60, 60);
	protected Color colorListBgSelected = new Color(10, 90, 10);
	protected Color colorListFg = new Color(200, 200, 200, 160);
	protected Color colorListFgSelected = new Color(230, 230, 230, 200);
	protected Color colorListEnclosureBg = new Color(150, 150, 150);
	protected Color colorListHeader = new Color(10, 10, 10);
	protected Color colorListDescription = new Color(60, 60, 60);


	protected JComponent configureList(JList<Object> list, String title, String description) {
		list.setFont(list.getFont().deriveFont(13f));
		list.setBackground(colorListBg);
		list.setForeground(colorListFg);
		list.setSelectionBackground(colorListBgSelected);
		list.setSelectionForeground(colorListFgSelected);
		list.setFont(list.getFont().deriveFont(12f));
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		list.setSelectionInterval(0, list.getModel().getSize() - 1);
		return configureAnyScrollable(list, title, description);
	}
	
	protected JComponent configureAnyScrollable(JComponent scrollable, String title, String description) {
		RoundedPanel enclosure = new RoundedPanel(10, 5, 5);
		enclosure.setBackground(colorListEnclosureBg);
		enclosure.setLayout(new BoxLayout(enclosure, BoxLayout.Y_AXIS));
		JLabel headerLabel = new JLabel(title);
		headerLabel.setOpaque(false);
		headerLabel.setForeground(colorListHeader);
		headerLabel.setFont(headerLabel.getFont().deriveFont(14f));
		JLabel descriptionLabel = new JLabel("<html>" + description + "</html>");
		descriptionLabel.setOpaque(false);
		descriptionLabel.setForeground(colorListDescription);
		descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(11f));
		JScrollPane listScrollPane = new JScrollPane(scrollable);
		listScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setViewportBorder(BorderFactory.createLineBorder(new Color(40, 40, 40)));
		listScrollPane.setBorder(BorderFactory.createEmptyBorder());
		JScrollBar vBar = listScrollPane.getVerticalScrollBar();
		vBar.setUI(new SlickerScrollBarUI(vBar, colorListEnclosureBg, new Color(30, 30, 30), new Color(80, 80, 80), 4,
				12));
		enclosure.add(packLeftAligned(headerLabel));
		enclosure.add(Box.createVerticalStrut(3));
		enclosure.add(packLeftAligned(descriptionLabel));
		enclosure.add(Box.createVerticalStrut(5));
		enclosure.add(listScrollPane);
		return enclosure;
	}

	protected JComponent packLeftAligned(JComponent component) {
		JPanel packed = new JPanel();
		packed.setOpaque(false);
		packed.setBorder(BorderFactory.createEmptyBorder());
		packed.setLayout(new BoxLayout(packed, BoxLayout.X_AXIS));
		packed.add(component);
		packed.add(Box.createHorizontalGlue());
		return packed;
	}

	
	private class StateSplitStep extends JPanel {
		
		private TransitionSystem ts;
		
		private JComponent comp;
		
		private JList<Object> list;
		
		private final String heading = "Transition system decomposition";
		
		private final String text = "Green states will belong to a specail transition system.";
		
		private Set<State> states;
		
		private List<State> sortedStates;
		
		private NiceSlider slider;
		
		public StateSplitStep(TransitionSystem ts) {
			this.ts = ts;
			states = ts.getNodes();
		}
		
		public void initComponents() {
			double size[][] = { { 80, TableLayoutConstants.FILL }, { TableLayoutConstants.FILL, 30, 30 } };
			setLayout(new BorderLayout());
			/**
			 * Initialize the selection list.
			 */
			if (states != null) {
				
					if (comp != null) {
						this.remove(comp);
					}
					sortedStates = new ArrayList<State>(states);
					Collections.sort(sortedStates, new StateComparator());
					list = new JList<Object>(sortedStates.toArray());
					comp = configureList(list, heading, text);
					this.add(comp, BorderLayout.CENTER);
			}
	}
		
	public List<Object> retrieveSpecialStates() {
			return list.getSelectedValuesList();
	}

	private class StateComparator implements Comparator<State> {

			public int compare(State o1, State o2) {
				return (new AlphanumComparator().compare(o1.getLabel(), o2.getLabel()));
			}
		}
	}
}
