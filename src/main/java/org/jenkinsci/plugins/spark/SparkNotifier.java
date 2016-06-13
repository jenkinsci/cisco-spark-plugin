package org.jenkinsci.plugins.spark;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

import org.jenkinsci.plugins.spark.client.SparkClient;
import org.jenkinsci.plugins.spark.token.SparkToken;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Mailer;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import net.sf.json.JSONObject;

public class SparkNotifier extends Notifier {

	public static final String DEFAULT_CONTENT_KEY = "${DEFAULT_CONTENT}";
	public static final String DEFAULT_CONTENT_VALUE = "${BUILD_STATUS}:${BUILD_URL}";

	private static final String CISCO_SPARK_PLUGIN_NAME = "[Cisco Spark Plugin]";

	private final boolean disable;
	private final boolean notnotifyifsuccess;
	private final boolean attachcodechange;
	private boolean invitetoroom;
	private boolean attachtestresult = true;
	private final String sparkRoomName;
	private final String publishContent;

	@DataBoundConstructor
	public SparkNotifier(boolean disable, boolean notnotifyifsuccess, String sparkRoomName, String publishContent,
	        boolean invitetoroom, boolean attachtestresult, boolean attachcodechange) {
		this.disable = disable;
		this.attachtestresult = attachtestresult;
		this.notnotifyifsuccess = notnotifyifsuccess;
		this.invitetoroom = invitetoroom;
		this.attachcodechange = attachcodechange;
		this.sparkRoomName = sparkRoomName;
		this.publishContent = publishContent;
		System.out.println(this.toString());
	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	public String getPublishContent() {
		return publishContent;
	}

	public String getSparkRoomName() {
		return sparkRoomName;
	}

	public boolean isDisable() {
		return disable;
	}

	public boolean isNotnotifyifsuccess() {
		return notnotifyifsuccess;
	}

	public boolean isInvitetoroom() {
		return invitetoroom;
	}

	public boolean isAttachcodechange() {
		return attachcodechange;
	}

	public boolean isAttachtestresult() {
		return attachtestresult;
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		PrintStream logger = listener.getLogger();
		logger.println(CISCO_SPARK_PLUGIN_NAME + this.toString());

		if (disable) {
			logger.println(CISCO_SPARK_PLUGIN_NAME
			        + "================[skiped: no need to notify due to the plugin disabled]=================");
			return true;
		}

		if (notnotifyifsuccess) {
			if (build.getResult() == Result.SUCCESS) {
				logger.println(CISCO_SPARK_PLUGIN_NAME
				        + "================[skiped: no need to notify due to success]=================");
				return true;
			}
		}

		notify(build, listener, logger);

		return true;
	}

	private void notify(AbstractBuild build, BuildListener listener, PrintStream logger) {
		logger.println(CISCO_SPARK_PLUGIN_NAME + "================[start]=================");
		try {
			DescriptorImpl descriptor = getDescriptor();
			SparkRoom sparkRoom = descriptor.getSparkRoom(sparkRoomName);
			SparkClient.sent(sparkRoom, "[message from cisco spark plugin for jenkins]");

			//notify content
			inviteCommittersIfNeed(build, logger, sparkRoom);
			atCommitters(build, sparkRoom, logger);
			notifyCustomizedContent(build, listener, logger, sparkRoom);
			if (attachtestresult)
				notifyTestResultIfExisted(build, sparkRoom, logger);
			if (attachcodechange)
				notifyCodeChanges(build, sparkRoom, logger);

			SparkClient.sent(sparkRoom, "[message from cisco spark plugin for jenkins]");
			logger.println(CISCO_SPARK_PLUGIN_NAME + "================[end][success]=================");
		} catch (Exception e) {
			logger.println(CISCO_SPARK_PLUGIN_NAME + e.getMessage());
			logger.println(CISCO_SPARK_PLUGIN_NAME + Arrays.toString(e.getStackTrace()));
			logger.println(CISCO_SPARK_PLUGIN_NAME + "================[end][failure]=================");
		}
	}

	private void inviteCommittersIfNeed(AbstractBuild build, PrintStream logger, SparkRoom sparkRoom) throws Exception {
		if (build.getResult() != Result.SUCCESS && isInvitetoroom()) {
			logger.println(
			        CISCO_SPARK_PLUGIN_NAME + "================[need invite committers to room]=================");
			HashSet<String> scmCommiterEmails = getScmCommiterEmails(build, sparkRoom, logger);
			SparkClient.invite(sparkRoom, scmCommiterEmails);
		}
	}

	private void notifyCustomizedContent(AbstractBuild build, BuildListener listener, PrintStream logger,
	        SparkRoom sparkRoom) throws MacroEvaluationException, IOException, InterruptedException, Exception {
		logger.println(CISCO_SPARK_PLUGIN_NAME + "[Expand content]Before Expand: " + publishContent);
		String publishContentAfterInitialExpand = publishContent;
		if (publishContent.contains(DEFAULT_CONTENT_KEY)) {
			publishContentAfterInitialExpand = publishContent.replace(DEFAULT_CONTENT_KEY, DEFAULT_CONTENT_VALUE);
		}
		logger.println(CISCO_SPARK_PLUGIN_NAME + "[Expand content]Expand: " + publishContentAfterInitialExpand);

		String expandAll = TokenMacro.expandAll(build, listener, publishContentAfterInitialExpand, false,
		        getPrivateMacros());
		logger.println(CISCO_SPARK_PLUGIN_NAME + "[Expand content]Expand: " + expandAll);

		logger.println(CISCO_SPARK_PLUGIN_NAME + "[Publish Content][begin]use:" + sparkRoom);
		SparkClient.sent(sparkRoom, expandAll);
	}

	private void notifyCodeChanges(AbstractBuild build, SparkRoom sparkRoom, PrintStream logger) throws Exception {
		ChangeLogSet<ChangeLogSet.Entry> changeSet = build.getChangeSet();
		Object[] items = changeSet.getItems();
		if (items.length > 0) {
			logger.println(CISCO_SPARK_PLUGIN_NAME + "[Publish Content]changes:");
			SparkClient.sent(sparkRoom, "[changes]");
		}
		for (Object entry : items) {
			ChangeLogSet.Entry entryCasted = (ChangeLogSet.Entry) entry;
			String content = "          " + entryCasted.getAuthor() + ":" + entryCasted.getAffectedPaths();
			logger.println(CISCO_SPARK_PLUGIN_NAME + "[Publish Content]" + content);
			SparkClient.sent(sparkRoom, content);
		}
	}

	/**
	 * FIXME
	 * 
	 * @param build
	 * @param sparkRoom
	 * @param logger
	 * @throws Exception
	 */
	private void notifyTestResultIfExisted(AbstractBuild build, SparkRoom sparkRoom, PrintStream logger)
	        throws Exception {
		try {
			AbstractTestResultAction testResultAction = build.getAction(AbstractTestResultAction.class);
			if (testResultAction != null) {
				logger.println(CISCO_SPARK_PLUGIN_NAME + "[Publish Content]test results:");
				SparkClient.sent(sparkRoom, "[test results]");
				int totalCount = testResultAction.getTotalCount();
				int failCount = testResultAction.getFailCount();
				int skipCount = testResultAction.getSkipCount();
				SparkClient.sent(sparkRoom,
				        String.format("          total:%d, failed:%d, skiped:%d", totalCount, failCount, skipCount));
				/*
				 * List failedTests = testResultAction.getFailedTests();
				 * if(failedTests.size()>0) SparkClient.sent(sparkRoom,
				 * "        failed test cases:" + failedTests);
				 */
			}
		} catch (Throwable throwable) {
			logger.println(CISCO_SPARK_PLUGIN_NAME + throwable.getMessage());
		}
	}

	private HashSet<String> getScmCommiterEmails(AbstractBuild build, SparkRoom sparkRoom, PrintStream logger)
	        throws Exception {
		Set<User> culprits = build.getCulprits();
		Iterator<User> iterator = culprits.iterator();
		HashSet<String> emails = new HashSet<String>();
		while (iterator.hasNext()) {
			User user = iterator.next();
			Mailer.UserProperty property = user.getProperty(Mailer.UserProperty.class);
			if (property != null) {
				String address = property.getAddress();
				if (address != null && address.contains("@"))
					emails.add(address);
			}

		}
		logger.println(CISCO_SPARK_PLUGIN_NAME + "[Publish Content][Committers Email]" + emails);
		return emails;
	}

	private void atCommitters(AbstractBuild build, SparkRoom sparkRoom, PrintStream logger) throws Exception {
		Set culprits = build.getCulprits();
		Iterator iterator = culprits.iterator();
		StringBuffer authors = new StringBuffer();
		while (iterator.hasNext()) {
			Object next = iterator.next();
			authors.append(" @" + next.toString());
		}
		logger.println(CISCO_SPARK_PLUGIN_NAME + "[Publish Content]" + authors.toString());
		SparkClient.sent(sparkRoom, authors.toString());
	}

	private static List<TokenMacro> getPrivateMacros() {
		List<TokenMacro> macros = new ArrayList<TokenMacro>();
		ClassLoader cl = Jenkins.getInstance().pluginManager.uberClassLoader;
		for (final IndexItem<SparkToken, TokenMacro> item : Index.load(SparkToken.class, TokenMacro.class, cl)) {
			try {
				macros.add(item.instance());
			} catch (Exception e) {
				// ignore errors loading tokens
			}
		}
		return macros;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}

	/**
	 * Descriptor for {@link SparkNotifier}. Used as a singleton. The class is
	 * marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See
	 * <tt>src/main/resources/jenkinsci/plugins/spark/SparkNotifier/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	// This indicates to Jenkins that this is an implementation of an extension
	// point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		/**
		 * To persist global configuration information, simply store it in a
		 * field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private final CopyOnWriteList<SparkRoom> sparkRooms = new CopyOnWriteList<SparkRoom>();

		public DescriptorImpl() {
			super(SparkNotifier.class);
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		/*
		 * public FormValidation doCheckName(@QueryParameter String value)
		 * throws IOException, ServletException { if (value.length() == 0)
		 * return FormValidation.error("Please set a name"); if (value.length()
		 * < 4) return FormValidation.warning("Isn't the name too short?");
		 * return FormValidation.ok(); }
		 */

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public SparkRoom[] getSparkRooms() {
			return sparkRooms.toArray(new SparkRoom[sparkRooms.size()]);
		}

		public SparkRoom getSparkRoom(String sparkRoomName) {
			for (SparkRoom sparkRoom : sparkRooms) {
				if (sparkRoom.getName().equalsIgnoreCase(sparkRoomName))
					return sparkRoom;
			}

			throw new RuntimeException("no such key: " + sparkRoomName);
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Cisco Spark Notification";
		}

		public FormValidation doNameCheck(@QueryParameter String name) throws IOException, ServletException {
			FormValidation basicVerify = returnVerify(name, "name");
			if (basicVerify.kind.equals(FormValidation.ok().kind)) {
				int total = 0;
				for (SparkRoom sparkRoom : sparkRooms) {
					if (sparkRoom.getName().equalsIgnoreCase(name.trim())) {
						total++;
					}
				}
				if (total > 1) {
					return FormValidation.error("duplicated name: " + name);
				}
				return FormValidation.ok();
			} else {
				return basicVerify;
			}
		}

		public FormValidation doTokenCheck(@QueryParameter String token) throws IOException, ServletException {
			return returnVerify(token, "Bearer token");
		}

		public FormValidation doSparkRoomIdCheck(@QueryParameter String sparkRoomId)
		        throws IOException, ServletException {
			return returnVerify(sparkRoomId, "spark room ID");
		}

		private FormValidation returnVerify(String value, String message) {
			if (null == value || value.length() == 0)
				return FormValidation.error("please input " + message);

			return FormValidation.ok();
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			sparkRooms.replaceBy(req.bindParametersToList(SparkRoom.class, "spark.room."));

			for (SparkRoom sparkRoom : sparkRooms) {
				System.out.println(sparkRoom);
			}
			save();
			return true;
		}

	}

	@Override
	public String toString() {
		return "SparkNotifier [disable=" + disable + ", notnotifyifsuccess=" + notnotifyifsuccess + ", invitetoroom="
		        + invitetoroom + ", attachcodechange=" + attachcodechange + ", attachtestresult=" + attachtestresult
		        + ", sparkRoomName=" + sparkRoomName + ", publishContent=" + publishContent + "]";
	}

}
